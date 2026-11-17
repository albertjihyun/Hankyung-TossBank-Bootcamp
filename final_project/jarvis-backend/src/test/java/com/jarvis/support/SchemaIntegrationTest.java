package com.jarvis.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 스키마 원본({@code docs/backend/schema.sql})이 실제 MariaDB에 올라가고, 엔티티가 그 스키마와 일치하며,
 * 문서가 "DB가 막는다"고 적은 제약이 정말 막는지 확인한다.
 */
class SchemaIntegrationTest extends MariaDbIntegrationTest {

    @Test
    @DisplayName("엔티티와 schema.sql이 일치한다 — ddl-auto=validate로 컨텍스트가 뜬다")
    void entitiesMatchSchema() {
        // 불일치(컬럼 누락·타입 차이)가 있으면 컨텍스트 기동이 실패해 이 클래스의 모든 테스트가 죽는다.
        // 운영에서는 같은 불일치가 배포 후 기동 거부로 드러났다(DEPLOY.md §4-2, 2026-07-31).
        Integer tables = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'", Integer.class);

        assertThat(tables).isEqualTo(23);
    }

    @Test
    @DisplayName("재고는 음수가 될 수 없다 — CHECK(quantity >= 0)가 앱을 우회한 UPDATE도 막는다 (02 D33)")
    void stockCannotGoNegative() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 1);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE product_stock SET quantity = quantity - 2 WHERE product_id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(data.stockQuantity(productId)).isEqualTo(1);
    }

    @Test
    @DisplayName("옵션 없는 상품의 재고 행은 하나뿐이다 — NULL은 UNIQUE에 안 걸리므로 가상 컬럼으로 막는다 (02 D44)")
    void singleStockRowForOptionlessProduct() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 5);

        assertThatThrownBy(() -> data.stock(productId, 7))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("회원당 기본 배송지는 하나뿐이다 — 가상 컬럼 UNIQUE (02 D44)")
    void singleDefaultAddressPerMember() {
        TestData data = new TestData(jdbc);
        long memberId = data.member();
        String insert = "INSERT INTO address (member_id, label, recipient, phone, zip_code, address1, is_default, "
                + "created_at) VALUES (?, 'home', 'r', 'p', 'z', 'a1', ?, NOW())";
        jdbc.update(insert, memberId, true);
        jdbc.update(insert, memberId, false);
        jdbc.update(insert, memberId, false);

        assertThatThrownBy(() -> jdbc.update(insert, memberId, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("재고 행의 옵션은 그 상품의 옵션이어야 한다 — 복합 FK가 남의 옵션을 거부한다 (02 D44)")
    void stockOptionMustBelongToProduct() {
        TestData data = new TestData(jdbc);
        long productA = data.product();
        long productB = data.product();
        long optionOfB = data.option(productB, "red");

        assertThatThrownBy(() -> data.stock(productA, optionOfB, 3))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("상품 가격 — 음수와 '판매가 > 정가'를 DB가 거부한다 (02 D51)")
    void productPriceIsConstrained() {
        TestData data = new TestData(jdbc);
        long productId = data.product();

        assertThatThrownBy(() -> jdbc.update("UPDATE product SET price = -1 WHERE id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE product SET price = original_price + 1 WHERE id = ?", productId))
                .isInstanceOf(DataIntegrityViolationException.class);
        // 경계값은 허용 — 0원 상품, 판매가 = 정가
        assertThat(jdbc.update("UPDATE product SET price = original_price WHERE id = ?", productId)).isEqualTo(1);
        assertThat(jdbc.update("UPDATE product SET price = 0 WHERE id = ?", productId)).isEqualTo(1);
    }

    @Test
    @DisplayName("주문 줄 — 수량 0·음수와 음수 금액을 DB가 거부한다. 매출 집계의 재료가 오염되지 않는다 (02 D51)")
    void orderItemAmountAndQuantityAreConstrained() {
        TestData data = new TestData(jdbc);
        long itemId = data.orderItem(data.order(data.member(), "PAID"), data.product(), "ORDERED");

        assertThatThrownBy(() -> jdbc.update("UPDATE order_item SET quantity = 0 WHERE id = ?", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_item SET quantity = -3 WHERE id = ?", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE order_item SET price = -1 WHERE id = ?", itemId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("주문 총액은 음수가 될 수 없다 (02 D51)")
    void orderTotalCannotBeNegative() {
        TestData data = new TestData(jdbc);
        long orderId = data.order(data.member(), "PAID");

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET total_amount = -1 WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
