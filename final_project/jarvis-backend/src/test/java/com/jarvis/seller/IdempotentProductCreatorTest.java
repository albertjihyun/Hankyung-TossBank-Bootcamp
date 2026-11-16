package com.jarvis.seller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.seller.dto.SellerProductCreateRequest;
import com.jarvis.seller.dto.SellerProductCreateResponse;
import com.jarvis.seller.dto.StockInput;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** I-10 멱등 입구 (02 D48 · 03 §3-1) — 같은 Idempotency-Key(AI의 draftId)가 두 번 와도 상품은 하나 */
@ExtendWith(MockitoExtension.class)
class IdempotentProductCreatorTest {

    private static final long BRAND_ID = 3L;
    private static final String DRAFT_ID = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b";

    @Mock SellerProductService sellerProductService;
    @InjectMocks IdempotentProductCreator creator;

    private final SellerProductCreateRequest request = new SellerProductCreateRequest(
            "새 상품", 10000, null, List.of(new StockInput(null, 100)), null, 20L, null, null, null, null, null);
    private final SellerProductCreateResponse first = new SellerProductCreateResponse(205L, "ON_SALE");

    @Test
    @DisplayName("키 없음 — 종전 경로 그대로(매 호출이 새 상품). 키를 보내지 않는 호출자를 깨지 않는다")
    void withoutKey_delegatesToPlainCreate() {
        when(sellerProductService.create(BRAND_ID, request)).thenReturn(first);

        assertThat(creator.create(BRAND_ID, request, null)).isSameAs(first);

        verify(sellerProductService, never()).findCreated(anyLong(), anyString());
    }

    @Test
    @DisplayName("첫 호출 — 같은 키의 상품이 없으면 키와 함께 등록한다")
    void firstCall_createsWithKey() {
        when(sellerProductService.findCreated(BRAND_ID, DRAFT_ID)).thenReturn(Optional.empty());
        when(sellerProductService.create(BRAND_ID, request, DRAFT_ID)).thenReturn(first);

        assertThat(creator.create(BRAND_ID, request, DRAFT_ID)).isSameAs(first);
    }

    @Test
    @DisplayName("AI 타임아웃 뒤 같은 초안을 다시 승인 — 새로 만들지 않고 처음 등록한 상품을 돌려준다")
    void reconfirmAfterTimeout_returnsExistingProduct() {
        when(sellerProductService.findCreated(BRAND_ID, DRAFT_ID)).thenReturn(Optional.of(first));

        assertThat(creator.create(BRAND_ID, request, DRAFT_ID)).isSameAs(first);

        verify(sellerProductService, never()).create(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("동시 도착 — UNIQUE에서 갈리고, 진 쪽은 이긴 쪽의 상품을 돌려준다")
    void concurrentArrival_loserReturnsWinnersProduct() {
        when(sellerProductService.findCreated(BRAND_ID, DRAFT_ID)).thenReturn(Optional.empty(), Optional.of(first));
        when(sellerProductService.create(BRAND_ID, request, DRAFT_ID))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_product_idempotency'"));

        assertThat(creator.create(BRAND_ID, request, DRAFT_ID)).isSameAs(first);
    }

    @Test
    @DisplayName("다른 제약 위반 — 같은 키의 상품이 없으면 '중복'으로 삼키지 않고 그대로 던진다")
    void unrelatedConstraintViolation_isNotSwallowed() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk_product_brand");
        when(sellerProductService.findCreated(BRAND_ID, DRAFT_ID)).thenReturn(Optional.empty());
        when(sellerProductService.create(BRAND_ID, request, DRAFT_ID)).thenThrow(other);

        assertThatThrownBy(() -> creator.create(BRAND_ID, request, DRAFT_ID)).isSameAs(other);
    }

    @Test
    @DisplayName("키는 브랜드 범위 — 다른 브랜드의 같은 키는 서로 영향을 주지 않는다")
    void keyIsScopedToBrand() {
        when(sellerProductService.findCreated(9L, DRAFT_ID)).thenReturn(Optional.empty());
        when(sellerProductService.create(9L, request, DRAFT_ID)).thenReturn(first);

        creator.create(9L, request, DRAFT_ID);

        verify(sellerProductService).findCreated(9L, DRAFT_ID);
        verify(sellerProductService, never()).findCreated(BRAND_ID, DRAFT_ID);
    }
}
