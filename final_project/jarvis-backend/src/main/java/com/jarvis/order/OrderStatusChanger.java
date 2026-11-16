package com.jarvis.order;

import com.jarvis.product.ProductStockService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 모든 주문/아이템 상태 전이의 단일 관문 (01 D12) — 상태 UPDATE와 order_status_logs INSERT를
 * 같은 트랜잭션으로 묶어 로그 누락을 구조적으로 차단한다. 이 클래스를 우회한 상태 변경은 리뷰에서 거부된다.
 *
 * 기록 규칙 (01 §6.5): *_REQUESTED(claim이 정본)·CONFIRMED(분석 미사용)·아이템 ORDERED(PAID와 동시) 미기록.
 * 아이템 상태 전이는 아이템마다 1행(orderItemId 필수), 주문 상태 전이만 null — 해상도는 "무엇의 상태를
 * 바꿨나"로 가른다(2026-08-06 개정, 구 "배치는 주문 단위 1행" 폐기).
 * 취소·반품 완료 actor는 승인 주체가 아니라 신청 주체(USER).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStatusChanger {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ClaimRepository claimRepository;
    private final OrderStatusLogRepository logRepository;
    private final ProductStockService productStockService;

    /** 배송 전이 한 번에 처리하는 상한 — 남은 건은 다음 틱이 가져간다 */
    static final int SHIPMENT_BATCH_SIZE = 500;

    /** O-1 주문 생성 — NULL → PENDING (SYSTEM) */
    public void logOrderCreated(Order order) {
        logRepository.save(OrderStatusLog.ofOrder(order.getId(), null,
                OrderStatus.PENDING.name(), ActorType.SYSTEM, null));
    }

    /** 모의 결제 실패 — reason에 실패 코드 (01 §6.5) */
    public void paymentFailed(Order order, String failureCode) {
        String from = order.getStatus().name();
        order.markPaymentFailed();
        logRepository.save(OrderStatusLog.ofOrder(order.getId(), from,
                OrderStatus.PAYMENT_FAILED.name(), ActorType.SYSTEM, failureCode));
    }

    /** 결제 성공 — Order PAID + 아이템 전량 ORDERED 같은 트랜잭션 (01 D9). 아이템 ORDERED 로그는 없음 */
    public void paymentSucceeded(Order order, List<OrderItem> items, LocalDateTime now) {
        String from = order.getStatus().name();
        order.markPaid(now);
        items.forEach(item -> item.markOrdered(now));
        logRepository.save(OrderStatusLog.ofOrder(order.getId(), from,
                OrderStatus.PAID.name(), ActorType.SYSTEM, null));
    }

    /**
     * 클레임 신청 접수 — 조건부 UPDATE로 동시 신청 중 한쪽만 성공 (01 §5).
     * *_REQUESTED는 로그 미기록 — 신청의 정본은 claim 테이블 (01 §6.5 규칙 1).
     */
    public boolean requestClaim(Long orderItemId, OrderItemStatus from, OrderItemStatus to, LocalDateTime now) {
        return orderItemRepository.transitionStatus(orderItemId, from, to, now) == 1;
    }

    /**
     * 배송 mock 전이 1단계 (01 §6) — 조건부 UPDATE(+status_changed_at 갱신) 후 아이템마다 로그 1행.
     * CONFIRMED 전이는 writeLog=false로 호출 (01 §6.5 규칙 2).
     */
    @Transactional
    public int transitionShipment(OrderItemStatus from, OrderItemStatus to,
                                  LocalDateTime threshold, boolean writeLog) {
        // 한 번에 SHIPMENT_BATCH_SIZE건까지만 — 밀린 건이 많아도 트랜잭션과 메모리가 묶인다. 나머지는 다음 틱
        List<OrderItem> candidates = orderItemRepository.findAllByStatusAndStatusChangedAtLessThanEqual(
                from, threshold, PageRequest.of(0, SHIPMENT_BATCH_SIZE, Sort.by("statusChangedAt", "id")));
        if (candidates.isEmpty()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (OrderItem item : candidates) {
            if (orderItemRepository.transitionStatus(item.getId(), from, to, now) == 1) {
                if (writeLog) {
                    // 아이템 상태 전이라 아이템마다 1행 (01 §6.5 규칙 4) — 구 주문 단위 묶음은 폐기
                    logRepository.save(OrderStatusLog.ofItem(item.getOrderId(), item.getId(),
                            from.name(), to.name(), ActorType.SYSTEM, null));
                }
                count++;
            }
        }
        return count;
    }

    /**
     * I-30 판매자 발송 (01 §2-3 — ORDERED→SHIPPING의 유일한 트리거, 2026-08-06 개정).
     * 조건부 UPDATE라 경합에 지면 0건이 돌아온다 — 이중 발송은 별도 락 없이 여기서 걸린다.
     * 성공하면 actor=SELLER로 아이템 단위 로그 1행을 남겨 I-14 분석에 합류시킨다.
     *
     * @return 전이했으면 true, 이미 다른 실행이 가져갔으면 false (호출부가 409로 번역)
     */
    @Transactional
    public boolean shipBySeller(OrderItem item, String reason, LocalDateTime now) {
        if (orderItemRepository.transitionStatus(
                item.getId(), OrderItemStatus.ORDERED, OrderItemStatus.SHIPPING, now) != 1) {
            return false;
        }
        logRepository.save(OrderStatusLog.ofItem(item.getOrderId(), item.getId(),
                OrderItemStatus.ORDERED.name(), OrderItemStatus.SHIPPING.name(),
                ActorType.SELLER, reason));
        return true;
    }

    /** 자동 승인 대상 클레임 id — 오래된 것부터 최대 {@code limit}건 (01 D10·§6) */
    @Transactional(readOnly = true)
    public List<Long> findDueClaimIds(LocalDateTime threshold, int limit) {
        return claimRepository.findDueIds(ClaimStatus.REQUESTED, threshold, PageRequest.of(0, limit));
    }

    /**
     * 클레임 한 건 자동 승인 (01 D10·§6) — 아이템 종결 전이 + claim COMPLETED + 재고 복원을 한 트랜잭션으로.
     * 로그는 아이템 1행, actor=USER(신청 주체), reason=claim.reason (01 §6.5 규칙 3·4).
     * 전량 취소 도달 시 orders.status → CANCELLED 승격 + 주문 단위 로그 1행 (01 §2-1).
     *
     * <p><b>건마다 독립 트랜잭션이다</b>(2026-11-16 — 구 "대상 전부를 한 트랜잭션"). 묶어서 처리하면 한 건의
     * 실패가 그 틱의 승인 전부를 롤백시키고, 같은 건이 매 틱 맨 앞에서 다시 실패해 전체가 멈춘다. 호출자
     * ({@link OrderMockScheduler})가 건별로 부르고 실패한 건만 건너뛴다.
     *
     * <p>확정분의 재고를 같은 트랜잭션에서 되돌린다(2026-08-10). 신청 시점이 아니라 이 승인 지점인 이유는,
     * 신청은 반려될 수 있는 상태라 재고를 미리 풀면 팔 수 없는 물건을 파는 셈이 되기 때문이다.
     *
     * @return 이 호출이 승인했으면 true. 이미 처리됐거나 아이템이 신청 상태가 아니면 false(아무것도 바꾸지 않는다)
     */
    @Transactional
    public boolean approveClaim(Long claimId, LocalDateTime now) {
        Claim claim = claimRepository.findById(claimId).orElse(null);
        if (claim == null || claim.getStatus() != ClaimStatus.REQUESTED) {
            return false;
        }
        OrderItem item = orderItemRepository.findById(claim.getOrderItemId()).orElse(null);
        if (item == null) {
            log.warn("클레임 {} 대상 아이템 {} 없음 — 건너뜀", claim.getId(), claim.getOrderItemId());
            return false;
        }
        OrderItemStatus from = claim.requestedItemStatus();
        OrderItemStatus to = claim.completedItemStatus();
        // 전이에 성공한 쪽만 재고를 되돌린다 — 다른 인스턴스가 먼저 처리했으면 0건이라 이중 복원이 없다
        if (orderItemRepository.transitionStatus(item.getId(), from, to, now) != 1) {
            return false;
        }
        claim.approve(now);
        logRepository.save(OrderStatusLog.ofItem(item.getOrderId(), item.getId(),
                from.name(), to.name(), ActorType.USER, claim.getReason()));
        // 취소·반품 모두 복원 대상이다
        productStockService.restore(Map.of(
                new ProductStockService.StockKey(item.getProductId(), item.getOptionId()), item.getQuantity()));
        if (to == OrderItemStatus.CANCELLED) {
            promoteIfFullyCancelled(item.getOrderId());
        }
        return true;
    }

    private void promoteIfFullyCancelled(Long orderId) {
        if (orderItemRepository.countByOrderIdAndStatusNot(orderId, OrderItemStatus.CANCELLED) != 0) {
            return;
        }
        orderRepository.findById(orderId)
                .filter(order -> order.getStatus() != OrderStatus.CANCELLED)
                .ifPresent(order -> {
                    String from = order.getStatus().name();
                    order.markCancelled();
                    logRepository.save(OrderStatusLog.ofOrder(orderId, from,
                            OrderStatus.CANCELLED.name(), ActorType.USER, null));
                });
    }
}
