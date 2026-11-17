package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * S-1 수집 커버리지 쿼리의 결과값을 실제 MariaDB에서 확인한다 — "PAID 주문 중 결제 이벤트로 관측된 주문 수".
 * 쿼리 모양을 바꾼 뒤에도 숫자가 같아야 한다.
 */
class CollectionCoverageIntegrationTest extends MariaDbIntegrationTest {

    @Autowired OrderItemRepository orderItemRepository;

    private static final LocalDateTime FROM = LocalDateTime.now().minusDays(7);
    private static final LocalDateTime TO = LocalDateTime.now().plusDays(1);

    private void purchaseEvent(long orderId, String createdAt) {
        jdbc.update("INSERT INTO behavior_events (session_key, client_event_id, event_type, properties, "
                        + "occurred_at, created_at) VALUES ('s', ?, 'purchase_complete', ?, " + createdAt + ", " + createdAt + ")",
                UUID.randomUUID().toString(), "{\"orderId\":" + orderId + ",\"amount\":12000}");
    }

    @Test
    @DisplayName("PAID 주문 3건 중 2건만 결제 이벤트가 있으면 관측 2 / 전체 3")
    void countsObservedOrders() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        long memberId = data.member();
        long[] orders = new long[3];
        for (int i = 0; i < 3; i++) {
            orders[i] = data.order(memberId, "PAID");
            data.orderItem(orders[i], productId, "ORDERED");
        }
        purchaseEvent(orders[0], "NOW()");
        purchaseEvent(orders[1], "NOW()");
        purchaseEvent(orders[1], "NOW()");   // 같은 주문의 이벤트가 둘이어도 한 번만 센다

        OrderItemRepository.CoverageRow row = orderItemRepository.aggregateCollectionCoverage(brandId, FROM, TO);

        assertThat(row.getPaidOrders()).isEqualTo(3);
        assertThat(row.getObservedOrders()).isEqualTo(2);
    }

    @Test
    @DisplayName("다른 브랜드의 주문·이벤트는 섞이지 않는다")
    void otherBrandsAreExcluded() {
        TestData data = new TestData(jdbc);
        long mine = data.brand();
        long other = data.brand();
        long memberId = data.member();
        long myOrder = data.order(memberId, "PAID");
        data.orderItem(myOrder, data.product(mine), "ORDERED");
        long otherOrder = data.order(memberId, "PAID");
        data.orderItem(otherOrder, data.product(other), "ORDERED");
        purchaseEvent(otherOrder, "NOW()");

        OrderItemRepository.CoverageRow row = orderItemRepository.aggregateCollectionCoverage(mine, FROM, TO);

        assertThat(row.getPaidOrders()).isEqualTo(1);
        assertThat(row.getObservedOrders()).isZero();
    }

    @Test
    @DisplayName("결제 이벤트가 기간보다 한참 전에 적재됐으면 관측으로 세지 않는다 — 이벤트도 기간으로 거른다")
    void eventsOutsideWindowAreIgnored() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long orderId = data.order(data.member(), "PAID");
        data.orderItem(orderId, data.product(brandId), "ORDERED");
        purchaseEvent(orderId, "NOW() - INTERVAL 30 DAY");

        OrderItemRepository.CoverageRow row = orderItemRepository.aggregateCollectionCoverage(brandId, FROM, TO);

        assertThat(row.getPaidOrders()).isEqualTo(1);
        assertThat(row.getObservedOrders()).isZero();
    }
}
