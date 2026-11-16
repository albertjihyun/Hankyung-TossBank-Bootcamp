package com.jarvis.order;

import com.jarvis.order.dto.OrderCreateRequest;
import com.jarvis.order.dto.OrderCreateResponse;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * O-1 주문 생성의 멱등 입구 (02 D47 · 03 §3-1 멱등성) — 같은 `Idempotency-Key`가 두 번 와도 주문은 하나다.
 *
 * <p>막는 것은 두 가지다. ① 응답이 유실돼 사용자가 다시 누른 경우 — 서버는 이미 결제·차감을 끝냈으므로
 * 새로 만들지 않고 그때의 결과를 돌려준다. ② 두 탭·더블클릭으로 같은 요청이 동시에 온 경우 — 둘 다
 * "아직 없음"을 보고 INSERT로 가지만 UNIQUE가 하나만 통과시킨다.
 *
 * <p>트랜잭션을 걸지 않는 것이 요점이다. 진 쪽은 제약 위반으로 자기 트랜잭션이 롤백된 뒤에 이긴 쪽의
 * 주문을 읽어야 하는데, 같은 트랜잭션 안에서는 그게 불가능하다(rollback-only). 그래서 "조회 → 생성 →
 * 실패 시 재조회"의 각 단계가 {@link OrderService}의 독립 트랜잭션으로 돈다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotentOrderCreator {

    private final OrderService orderService;

    public OrderCreateResponse create(Long memberId, OrderCreateRequest request, String sessionKey,
                                      String idempotencyKey) {
        if (idempotencyKey == null) {
            return orderService.create(memberId, request, sessionKey);
        }
        Optional<OrderCreateResponse> created = orderService.findCreated(memberId, idempotencyKey);
        if (created.isPresent()) {
            log.info("O-1 멱등 재전송 — 기존 주문 반환 (memberId={}, orderId={})",
                    memberId, created.get().orderId());
            return created.get();
        }
        try {
            return orderService.create(memberId, request, sessionKey, idempotencyKey);
        } catch (DataIntegrityViolationException e) {
            // 동시 도착에서 진 쪽. 같은 키의 주문이 정말 생겼을 때만 성공으로 돌린다 —
            // 다른 제약 위반까지 "중복"으로 삼키지 않도록 재조회 결과로 판정한다(03 §3-1).
            return orderService.findCreated(memberId, idempotencyKey).orElseThrow(() -> e);
        }
    }
}
