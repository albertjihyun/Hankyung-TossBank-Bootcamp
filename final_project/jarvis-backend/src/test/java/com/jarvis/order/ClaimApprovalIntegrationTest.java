package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.jarvis.product.ProductStockService;
import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * 클레임 자동 승인을 실제 MariaDB에서 확인한다 (01 D10·§6) — 아이템 종결·클레임 완료·재고 복원·주문 취소
 * 승격이 한 트랜잭션으로 묶이고, 같은 클레임을 여러 인스턴스가 동시에 처리해도 재고가 한 번만 돌아온다.
 */
@Import({OrderStatusChanger.class, ProductStockService.class})
class ClaimApprovalIntegrationTest extends MariaDbIntegrationTest {

    @Autowired OrderStatusChanger statusChanger;

    private long claim(long orderItemId, String type) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO claim (order_item_id, type, status, reason, created_at) "
                            + "VALUES (?, ?, 'REQUESTED', '단순변심', NOW() - INTERVAL 1 HOUR)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, orderItemId);
            statement.setString(2, type);
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }

    private String itemStatus(long itemId) {
        return jdbc.queryForObject("SELECT status FROM order_item WHERE id = ?", String.class, itemId);
    }

    private String claimStatus(long claimId) {
        return jdbc.queryForObject("SELECT status FROM claim WHERE id = ?", String.class, claimId);
    }

    @Test
    @DisplayName("취소 승인 — 아이템 CANCELLED, 클레임 COMPLETED, 재고 복원, 마지막 아이템이면 주문도 CANCELLED")
    void cancelApprovalCompletesEverythingTogether() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 4);
        long orderId = data.order(data.member(), "PAID");
        long itemId = data.orderItem(orderId, productId, "CANCEL_REQUESTED");
        jdbc.update("UPDATE order_item SET quantity = 3 WHERE id = ?", itemId);
        long claimId = claim(itemId, "CANCEL");

        boolean approved = statusChanger.approveClaim(claimId, LocalDateTime.now());

        assertThat(approved).isTrue();
        assertThat(itemStatus(itemId)).isEqualTo("CANCELLED");
        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
        assertThat(data.stockQuantity(productId)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM order_status_logs WHERE order_id = ?", Integer.class, orderId))
                .as("아이템 전이 1행 + 주문 취소 승격 1행").isEqualTo(2);
    }

    @Test
    @DisplayName("부분 취소 — 다른 아이템이 남아 있으면 주문은 PAID로 남는다")
    void partialCancelKeepsOrderPaid() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 0);
        long orderId = data.order(data.member(), "PAID");
        long cancelling = data.orderItem(orderId, productId, "CANCEL_REQUESTED");
        data.orderItem(orderId, productId, "ORDERED");
        long claimId = claim(cancelling, "CANCEL");

        statusChanger.approveClaim(claimId, LocalDateTime.now());

        assertThat(itemStatus(cancelling)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PAID");
    }

    @Test
    @DisplayName("반품 승인 — 아이템 RETURNED, 재고 복원. 주문은 취소로 바뀌지 않는다")
    void returnApprovalRestoresStock() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 1);
        long orderId = data.order(data.member(), "PAID");
        long itemId = data.orderItem(orderId, productId, "RETURN_REQUESTED");
        long claimId = claim(itemId, "RETURN");

        statusChanger.approveClaim(claimId, LocalDateTime.now());

        assertThat(itemStatus(itemId)).isEqualTo("RETURNED");
        assertThat(data.stockQuantity(productId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PAID");
    }

    @Test
    @DisplayName("같은 클레임을 4개 인스턴스가 동시에 승인 — 한 번만 승인되고 재고도 한 번만 돌아온다")
    void concurrentApprovalRestoresStockOnce() throws Exception {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 10);
        long orderId = data.order(data.member(), "PAID");
        long itemId = data.orderItem(orderId, productId, "CANCEL_REQUESTED");
        jdbc.update("UPDATE order_item SET quantity = 5 WHERE id = ?", itemId);
        long claimId = claim(itemId, "CANCEL");

        List<Callable<Boolean>> instances = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            instances.add(() -> statusChanger.approveClaim(claimId, LocalDateTime.now()));
        }
        List<Outcome<Boolean>> outcomes = Concurrently.run(instances);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes.stream().filter(Outcome::value).count()).isEqualTo(1);
        assertThat(data.stockQuantity(productId)).as("10 + 5, 이중 복원 없음").isEqualTo(15);
        assertThat(claimStatus(claimId)).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("아이템이 신청 상태가 아니면 승인하지 않는다 — 클레임·재고·아이템 모두 그대로")
    void itemNotInRequestedStateIsLeftAlone() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 3);
        long itemId = data.orderItem(data.order(data.member(), "PAID"), productId, "SHIPPING");
        long claimId = claim(itemId, "CANCEL");

        boolean approved = statusChanger.approveClaim(claimId, LocalDateTime.now());

        assertThat(approved).isFalse();
        assertThat(itemStatus(itemId)).isEqualTo("SHIPPING");
        assertThat(claimStatus(claimId)).isEqualTo("REQUESTED");
        assertThat(data.stockQuantity(productId)).isEqualTo(3);
    }

    @Test
    @DisplayName("대상 조회 — 기한이 지난 신청만, 오래된 것부터, 요청한 건수까지만")
    void dueLookupIsOrderedAndBounded() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        long orderId = data.order(data.member(), "PAID");
        List<Long> claimIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            claimIds.add(claim(data.orderItem(orderId, productId, "CANCEL_REQUESTED"), "CANCEL"));
        }
        // 가장 최근 것은 아직 기한 전으로 돌린다
        jdbc.update("UPDATE claim SET created_at = NOW() WHERE id = ?", claimIds.get(4));
        // 순서를 뒤섞는다 — id가 아니라 신청 시각 순이어야 한다
        jdbc.update("UPDATE claim SET created_at = NOW() - INTERVAL 5 HOUR WHERE id = ?", claimIds.get(3));

        List<Long> due = statusChanger.findDueClaimIds(LocalDateTime.now().minusMinutes(5), 3);

        assertThat(due).containsExactly(claimIds.get(3), claimIds.get(0), claimIds.get(1));
    }
}
