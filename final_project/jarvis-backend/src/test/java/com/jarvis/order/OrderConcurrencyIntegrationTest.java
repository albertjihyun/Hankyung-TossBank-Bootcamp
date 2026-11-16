package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 주문의 두 가지 경합을 실제 MariaDB에서 확인한다.
 * ① 같은 멱등 키의 주문은 하나만 저장된다(02 D47) ② 주문 상품의 상태 전이는 동시에 와도 한쪽만 이긴다(01 §6).
 */
class OrderConcurrencyIntegrationTest extends MariaDbIntegrationTest {

    private static final String KEY = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b";

    @Autowired OrderRepository orderRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private Order newOrder(long memberId, String idempotencyKey) {
        return Order.create(memberId, "MOCK_CARD", 12000, "r", "p", "z", "a1", null, null, idempotencyKey);
    }

    // ---- ① 멱등 키 ----

    @Test
    @DisplayName("같은 회원·같은 키로 8건이 동시에 저장을 시도 — 1건만 저장되고 나머지는 제약 위반")
    void sameKeyStoresExactlyOneOrder() throws Exception {
        long memberId = new TestData(jdbc).member();

        List<Callable<Long>> attempts = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            attempts.add(() -> orderRepository.saveAndFlush(newOrder(memberId, KEY)).getId());
        }
        List<Outcome<Long>> outcomes = Concurrently.run(attempts);

        assertThat(outcomes.stream().filter(Outcome::succeeded).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).map(Outcome::error))
                .allMatch(DataIntegrityViolationException.class::isInstance);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id = ?", Integer.class, memberId))
                .isEqualTo(1);
        // 진 쪽이 돌려줄 주문 — IdempotentOrderCreator가 제약 위반 뒤에 이 조회로 이긴 쪽 주문을 찾는다
        Long winner = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().value();
        assertThat(orderRepository.findByMemberIdAndIdempotencyKey(memberId, KEY))
                .get().extracting(Order::getId).isEqualTo(winner);
    }

    @Test
    @DisplayName("키는 회원 범위 — 다른 회원은 같은 키를 쓸 수 있고 서로의 주문이 조회되지 않는다")
    void keyIsScopedToMember() {
        TestData data = new TestData(jdbc);
        long alice = data.member();
        long bob = data.member();

        Long aliceOrder = orderRepository.saveAndFlush(newOrder(alice, KEY)).getId();
        Long bobOrder = orderRepository.saveAndFlush(newOrder(bob, KEY)).getId();

        assertThat(orderRepository.findByMemberIdAndIdempotencyKey(alice, KEY))
                .get().extracting(Order::getId).isEqualTo(aliceOrder);
        assertThat(orderRepository.findByMemberIdAndIdempotencyKey(bob, KEY))
                .get().extracting(Order::getId).isEqualTo(bobOrder);
    }

    @Test
    @DisplayName("키 없는 주문은 제한 없이 쌓인다 — NULL은 UNIQUE에 걸리지 않는다(헤더가 선택인 근거)")
    void ordersWithoutKeyAreUnconstrained() {
        long memberId = new TestData(jdbc).member();

        orderRepository.saveAndFlush(newOrder(memberId, null));
        orderRepository.saveAndFlush(newOrder(memberId, null));
        orderRepository.saveAndFlush(newOrder(memberId, null));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id = ?", Integer.class, memberId))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("같은 키를 순차로 다시 저장해도 거부된다 — 응답 유실 후 재시도가 새 주문을 만들지 못한다")
    void sequentialReplayIsRejectedByConstraint() {
        long memberId = new TestData(jdbc).member();
        orderRepository.saveAndFlush(newOrder(memberId, KEY));

        assertThatThrownBy(() -> orderRepository.saveAndFlush(newOrder(memberId, KEY)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- ② 상태 전이 ----

    @Test
    @DisplayName("취소 신청과 판매자 발송이 동시에 도착 — 20회 반복해도 매번 한쪽만 전이에 성공한다")
    void cancelAndShipRaceHasSingleWinner() throws Exception {
        TestData data = new TestData(jdbc);
        long memberId = data.member();
        long productId = data.product();

        for (int round = 0; round < 20; round++) {
            long orderId = data.order(memberId, "PAID");
            long itemId = data.orderItem(orderId, productId, "ORDERED");

            List<Outcome<Integer>> outcomes = Concurrently.run(List.of(
                    () -> transition(itemId, OrderItemStatus.ORDERED, OrderItemStatus.CANCEL_REQUESTED),
                    () -> transition(itemId, OrderItemStatus.ORDERED, OrderItemStatus.SHIPPING)));

            assertThat(outcomes).allMatch(Outcome::succeeded);
            assertThat(outcomes.stream().mapToInt(Outcome::value).sum())
                    .as("라운드 %d — 갱신된 행 수의 합", round).isEqualTo(1);
            String status = jdbc.queryForObject("SELECT status FROM order_item WHERE id = ?", String.class, itemId);
            assertThat(status).isIn("CANCEL_REQUESTED", "SHIPPING");
        }
    }

    @Test
    @DisplayName("같은 전이를 스케줄러 4대가 동시에 시도 — 1대만 성공한다(분산 락이 풀려도 중복 전이 없음)")
    void duplicateSchedulerRunsTransitionOnce() throws Exception {
        TestData data = new TestData(jdbc);
        long orderId = data.order(data.member(), "PAID");
        long itemId = data.orderItem(orderId, data.product(), "SHIPPING");

        List<Callable<Integer>> schedulers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            schedulers.add(() -> transition(itemId, OrderItemStatus.SHIPPING, OrderItemStatus.DELIVERED));
        }
        List<Outcome<Integer>> outcomes = Concurrently.run(schedulers);

        assertThat(outcomes.stream().mapToInt(Outcome::value).sum()).isEqualTo(1);
    }

    @Test
    @DisplayName("현재 상태가 다르면 전이하지 않는다 — 이미 배송 중인 상품은 취소 신청으로 바뀌지 않는다")
    void transitionRequiresExpectedCurrentStatus() {
        TestData data = new TestData(jdbc);
        long orderId = data.order(data.member(), "PAID");
        long itemId = data.orderItem(orderId, data.product(), "SHIPPING");

        int updated = transition(itemId, OrderItemStatus.ORDERED, OrderItemStatus.CANCEL_REQUESTED);

        assertThat(updated).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM order_item WHERE id = ?", String.class, itemId))
                .isEqualTo("SHIPPING");
    }

    private int transition(long itemId, OrderItemStatus from, OrderItemStatus to) {
        return transactionTemplate.execute(status ->
                orderItemRepository.transitionStatus(itemId, from, to, LocalDateTime.now()));
    }
}
