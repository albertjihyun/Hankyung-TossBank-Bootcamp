package com.jarvis.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.jarvis.product.ProductStockService.StockKey;
import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 재고 차감의 동시성 (02 D33 · 03 §3-1) — "조건부 UPDATE 한 방이면 초과 판매가 없다"는 설계 주장을
 * 실제 MariaDB에서 여러 스레드로 증명한다. 단위 테스트는 리포지토리가 0을 돌려준다고 가정할 뿐이라
 * 이 주장을 증명하지 못한다.
 */
@Import(ProductStockService.class)
class ProductStockConcurrencyIntegrationTest extends MariaDbIntegrationTest {

    @Autowired ProductStockService productStockService;

    @Test
    @DisplayName("재고 10개에 40명이 동시에 1개씩 주문 — 정확히 10명만 성공하고 재고는 0, 음수 없음")
    void noOversellUnderContention() throws Exception {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 10);
        StockKey key = new StockKey(productId, null);

        List<Callable<Boolean>> buyers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            buyers.add(() -> productStockService.deduct(Map.of(key, 1)));
        }
        List<Outcome<Boolean>> outcomes = Concurrently.run(buyers);

        assertThat(outcomes).allMatch(Outcome::succeeded, "예외 없이 성공/실패로만 갈린다");
        assertThat(outcomes.stream().filter(Outcome::value).count()).isEqualTo(10);
        assertThat(data.stockQuantity(productId)).isZero();
    }

    @Test
    @DisplayName("마지막 1개를 두 명이 동시에 주문 — 한 명만 산다")
    void lastOneGoesToExactlyOneBuyer() throws Exception {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 1);
        StockKey key = new StockKey(productId, null);

        List<Outcome<Boolean>> outcomes = Concurrently.run(List.of(
                () -> productStockService.deduct(Map.of(key, 1)),
                () -> productStockService.deduct(Map.of(key, 1))));

        assertThat(outcomes.stream().filter(Outcome::value).count()).isEqualTo(1);
        assertThat(data.stockQuantity(productId)).isZero();
    }

    @Test
    @DisplayName("두 상품을 서로 반대 순서로 담은 주문 40건이 동시에 와도 데드락이 없다 — 잠금 순서를 관문이 정렬한다")
    void noDeadlockWhenLinesArriveInOppositeOrder() throws Exception {
        TestData data = new TestData(jdbc);
        long productA = data.product();
        long productB = data.product();
        data.stock(productA, 100);
        data.stock(productB, 100);
        StockKey a = new StockKey(productA, null);
        StockKey b = new StockKey(productB, null);

        List<Callable<Boolean>> orders = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            // 입력 순서를 번갈아 뒤집는다 — 정렬이 없다면 A→B와 B→A가 서로의 행을 기다리며 데드락이 난다
            Map<StockKey, Integer> lines = new LinkedHashMap<>();
            if (i % 2 == 0) {
                lines.put(a, 1);
                lines.put(b, 1);
            } else {
                lines.put(b, 1);
                lines.put(a, 1);
            }
            orders.add(() -> productStockService.deduct(lines));
        }
        List<Outcome<Boolean>> outcomes = Concurrently.run(orders);

        assertThat(outcomes).allMatch(Outcome::succeeded, "데드락·락 타임아웃 예외가 없다");
        assertThat(outcomes).allMatch(Outcome::value);
        assertThat(data.stockQuantity(productA)).isEqualTo(60);
        assertThat(data.stockQuantity(productB)).isEqualTo(60);
    }

    @Test
    @DisplayName("여러 품목 중 하나가 부족하면 이미 뺀 품목을 되돌린다 — 부분 차감 상태로 남지 않는다")
    void partialFailureRestoresAlreadyDeducted() {
        TestData data = new TestData(jdbc);
        long inStock = data.product();
        long soldOut = data.product();
        data.stock(inStock, 5);
        data.stock(soldOut, 0);

        boolean deducted = productStockService.deduct(Map.of(
                new StockKey(inStock, null), 2,
                new StockKey(soldOut, null), 1));

        assertThat(deducted).isFalse();
        assertThat(data.stockQuantity(inStock)).isEqualTo(5);
        assertThat(data.stockQuantity(soldOut)).isZero();
    }

    @Test
    @DisplayName("옵션별 재고는 따로 준다 — 같은 상품의 다른 옵션 재고를 건드리지 않는다 (02 D33 개정)")
    void deductsOnlyTheChosenOption() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        long red = data.option(productId, "red");
        long blue = data.option(productId, "blue");
        data.stock(productId, red, 3);
        data.stock(productId, blue, 3);

        boolean deducted = productStockService.deduct(Map.of(new StockKey(productId, red), 3));

        assertThat(deducted).isTrue();
        assertThat(quantityOf(productId, red)).isZero();
        assertThat(quantityOf(productId, blue)).isEqualTo(3);
    }

    @Test
    @DisplayName("품절로 바뀌는 순간에만 변경 로그를 남긴다 — 재고가 0이 된 차감 1건당 로그 1건")
    void logsOnlyTheTransitionToSoldOut() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 2);
        StockKey key = new StockKey(productId, null);

        productStockService.deduct(Map.of(key, 1));
        Integer afterFirst = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_change_logs WHERE product_id = ?", Integer.class, productId);
        productStockService.deduct(Map.of(key, 1));
        Integer afterSoldOut = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_change_logs WHERE product_id = ?", Integer.class, productId);

        assertThat(afterFirst).isZero();
        assertThat(afterSoldOut).isEqualTo(1);
    }

    private int quantityOf(long productId, long optionId) {
        return jdbc.queryForObject(
                "SELECT quantity FROM product_stock WHERE product_id = ? AND option_id = ?",
                Integer.class, productId, optionId);
    }
}
