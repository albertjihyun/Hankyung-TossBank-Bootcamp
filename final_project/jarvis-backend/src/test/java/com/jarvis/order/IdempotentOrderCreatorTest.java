package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.order.dto.OrderCreateRequest;
import com.jarvis.order.dto.OrderCreateResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** O-1 멱등 입구 (02 D47 · 03 §3-1) — 같은 Idempotency-Key가 두 번 와도 주문은 하나 */
@ExtendWith(MockitoExtension.class)
class IdempotentOrderCreatorTest {

    private static final String KEY = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b";

    @Mock OrderService orderService;
    @InjectMocks IdempotentOrderCreator creator;

    private final OrderCreateRequest request = new OrderCreateRequest(null,
            List.of(new OrderCreateRequest.OrderLine(10L, null, 1, null)), 5L, null, null, "MOCK_CARD");
    private final OrderCreateResponse first = new OrderCreateResponse(1L, "ORD-20261116-1", "PAID", null);

    @Test
    @DisplayName("키 없음 — 종전 경로 그대로(매 요청이 새 주문). 기존 프론트·테스트를 깨지 않는다")
    void withoutKey_delegatesToPlainCreate() {
        when(orderService.create(1L, request, "sk")).thenReturn(first);

        assertThat(creator.create(1L, request, "sk", null)).isSameAs(first);

        verify(orderService, never()).findCreated(anyLong(), anyString());
    }

    @Test
    @DisplayName("첫 요청 — 같은 키의 주문이 없으면 키와 함께 생성한다")
    void firstRequest_createsWithKey() {
        when(orderService.findCreated(1L, KEY)).thenReturn(Optional.empty());
        when(orderService.create(1L, request, "sk", KEY)).thenReturn(first);

        assertThat(creator.create(1L, request, "sk", KEY)).isSameAs(first);
    }

    @Test
    @DisplayName("응답 유실 후 재전송 — 새로 만들지 않고 기존 주문의 결과를 돌려준다(결제·재고 재실행 없음)")
    void replay_returnsExistingWithoutCreating() {
        when(orderService.findCreated(1L, KEY)).thenReturn(Optional.of(first));

        assertThat(creator.create(1L, request, "sk", KEY)).isSameAs(first);

        verify(orderService, never()).create(anyLong(), any(), any(), anyString());
    }

    @Test
    @DisplayName("결제 실패했던 주문의 재전송 — 실패 결과를 그대로 돌려준다(프론트가 O-2 재결제로 잇는다)")
    void replayOfFailedPayment_returnsFailure() {
        OrderCreateResponse failed = new OrderCreateResponse(1L, "ORD-20261116-1", "PAYMENT_FAILED", "CARD_DECLINED");
        when(orderService.findCreated(1L, KEY)).thenReturn(Optional.of(failed));

        OrderCreateResponse response = creator.create(1L, request, "sk", KEY);

        assertThat(response.status()).isEqualTo("PAYMENT_FAILED");
        assertThat(response.failureReason()).isEqualTo("CARD_DECLINED");
    }

    @Test
    @DisplayName("동시 도착 — 둘 다 '없음'을 봐도 UNIQUE에서 갈린다. 진 쪽은 이긴 쪽의 주문을 돌려준다")
    void concurrentArrival_loserReturnsWinnersOrder() {
        when(orderService.findCreated(1L, KEY)).thenReturn(Optional.empty(), Optional.of(first));
        when(orderService.create(1L, request, "sk", KEY))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_orders_idempotency'"));

        assertThat(creator.create(1L, request, "sk", KEY)).isSameAs(first);
    }

    @Test
    @DisplayName("다른 제약 위반 — 같은 키의 주문이 없으면 '중복'으로 삼키지 않고 그대로 던진다")
    void unrelatedConstraintViolation_isNotSwallowed() {
        DataIntegrityViolationException other = new DataIntegrityViolationException("fk_orders_member");
        when(orderService.findCreated(1L, KEY)).thenReturn(Optional.empty());
        when(orderService.create(1L, request, "sk", KEY)).thenThrow(other);

        assertThatThrownBy(() -> creator.create(1L, request, "sk", KEY)).isSameAs(other);
    }

    @Test
    @DisplayName("키는 회원 범위 — 조회와 생성이 모두 요청한 회원의 id로 나간다(남의 키로 남의 주문을 볼 수 없다)")
    void keyIsScopedToMember() {
        when(orderService.findCreated(2L, KEY)).thenReturn(Optional.empty());
        when(orderService.create(2L, request, null, KEY)).thenReturn(first);

        creator.create(2L, request, null, KEY);

        verify(orderService).findCreated(2L, KEY);
        verify(orderService, never()).findCreated(1L, KEY);
    }
}
