package com.jarvis.seller;

import com.jarvis.seller.dto.SellerProductCreateRequest;
import com.jarvis.seller.dto.SellerProductCreateResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * I-10 상품 등록의 멱등 입구 (02 D48 · 03 §3-1 멱등성) — 같은 `Idempotency-Key`가 두 번 와도 상품은 하나다.
 *
 * <p>막는 상황: AI 서버는 이 호출을 3초 안에 답이 없으면 실패로 처리하고 판매자에게 "다시 시도"를 안내한다.
 * 그런데 등록은 이미지 복사(S3) 때문에 3초를 넘겨서도 성공할 수 있다. 판매자가 같은 초안을 다시 승인하면
 * 같은 상품이 한 번 더 등록됐다. AI가 초안 id(draftId)를 키로 실어 보내면, 두 번째 호출은 새로 만들지
 * 않고 처음 등록한 상품을 돌려준다.
 *
 * <p>구조는 주문의 {@code IdempotentOrderCreator}와 같다 — 트랜잭션을 걸지 않고 "조회 → 생성 → 제약 위반 시
 * 재조회"의 각 단계를 {@link SellerProductService}의 독립 트랜잭션으로 돌린다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentProductCreator {

    private final SellerProductService sellerProductService;

    public SellerProductCreateResponse create(Long brandId, SellerProductCreateRequest request,
                                              String idempotencyKey) {
        if (idempotencyKey == null) {
            return sellerProductService.create(brandId, request);
        }
        Optional<SellerProductCreateResponse> created = sellerProductService.findCreated(brandId, idempotencyKey);
        if (created.isPresent()) {
            log.info("I-10 멱등 재전송 — 기존 상품 반환 (brandId={}, productId={})",
                    brandId, created.get().productId());
            return created.get();
        }
        try {
            return sellerProductService.create(brandId, request, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // 같은 키의 상품이 정말 생겼을 때만 성공으로 돌린다 — 다른 제약 위반은 삼키지 않는다(03 §3-1)
            return sellerProductService.findCreated(brandId, idempotencyKey).orElseThrow(() -> e);
        }
    }
}
