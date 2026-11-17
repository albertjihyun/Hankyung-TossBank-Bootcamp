package com.jarvis.seller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.global.cache.RedisCache;
import com.jarvis.product.ProductStockService;
import com.jarvis.product.ProductStockService.StockKey;
import com.jarvis.seller.dto.SellerProductUpdateRequest;
import com.jarvis.seller.dto.SellerProductUpdateResponse;
import com.jarvis.seller.dto.StockInput;
import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * I-11 재고 수정의 재전송을 실제 MariaDB에서 확인한다 (02 D52).
 *
 * <p>재고 수정은 절대값으로 덮어쓴다. AI 서버가 이 호출을 타임아웃으로 실패 처리한 뒤 판매자가 같은 초안을
 * 다시 승인하면, 그 사이 팔린 수량이 되살아났다 — 없는 재고를 다시 팔게 된다.
 */
@Import({SellerProductService.class, ProductStockService.class})
class SellerStockUpdateIntegrationTest extends MariaDbIntegrationTest {

    private static final String DRAFT_ID = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b";

    @Autowired SellerProductService sellerProductService;
    @Autowired ProductStockService productStockService;

    @MockitoBean RedisCache cache;
    @MockitoBean ProductImageStorage productImageStorage;
    @MockitoBean ObjectMapper objectMapper;

    private static SellerProductUpdateRequest stockTo(int quantity) {
        return new SellerProductUpdateRequest(null, null, null, null, null, null, null,
                List.of(new StockInput(null, quantity)), null, null);
    }

    @Test
    @DisplayName("타임아웃 뒤 같은 초안을 다시 승인 — 그 사이 팔린 3개가 되살아나지 않는다")
    void replayDoesNotResurrectSoldStock() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        data.stock(productId, 10);

        // 1차: 판매자가 재고를 20으로 수정 (AI는 응답을 못 받고 실패로 안내)
        sellerProductService.updateInternal(brandId, productId, stockTo(20), DRAFT_ID);
        // 그 사이 3개가 팔렸다
        assertThat(productStockService.deduct(Map.of(new StockKey(productId, null), 3))).isTrue();
        // 2차: 판매자가 같은 초안을 다시 승인
        SellerProductUpdateResponse replay =
                sellerProductService.updateInternal(brandId, productId, stockTo(20), DRAFT_ID);

        assertThat(data.stockQuantity(productId)).as("20 - 3 = 17 그대로. 다시 20이 되면 3개를 또 판다").isEqualTo(17);
        assertThat(replay.changes()).as("응답은 처음 승인과 같은 모양").contains("STOCK");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_change_logs WHERE product_id = ? AND change_type = 'STOCK'",
                Integer.class, productId)).as("변경 로그도 한 번만").isEqualTo(1);
    }

    @Test
    @DisplayName("키 없이 같은 수정이 다시 오면 종전대로 덮어쓴다 — 키가 이 문제를 막는 유일한 장치다")
    void withoutKeyReplayOverwrites() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        data.stock(productId, 10);

        sellerProductService.updateInternal(brandId, productId, stockTo(20));
        productStockService.deduct(Map.of(new StockKey(productId, null), 3));
        sellerProductService.updateInternal(brandId, productId, stockTo(20));

        assertThat(data.stockQuantity(productId)).isEqualTo(20);
    }

    @Test
    @DisplayName("다른 초안(다른 키)의 수정은 정상 적용된다 — 키는 '같은 승인'만 묶는다")
    void differentDraftApplies() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        data.stock(productId, 10);

        sellerProductService.updateInternal(brandId, productId, stockTo(20), DRAFT_ID);
        sellerProductService.updateInternal(brandId, productId, stockTo(5), "another-draft-0123456789");

        assertThat(data.stockQuantity(productId)).isEqualTo(5);
    }

    @Test
    @DisplayName("옵션별 재고 — 같은 초안의 재전송은 이미 바꾼 옵션만 건너뛴다")
    void replaySkipsPerOption() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        long red = data.option(productId, "red");
        long blue = data.option(productId, "blue");
        data.stock(productId, red, 10);
        data.stock(productId, blue, 10);
        SellerProductUpdateRequest redTo20 = new SellerProductUpdateRequest(null, null, null, null, null, null,
                null, List.of(new StockInput(red, 20)), null, null);

        sellerProductService.updateInternal(brandId, productId, redTo20, DRAFT_ID);
        productStockService.deduct(Map.of(new StockKey(productId, red), 4));
        sellerProductService.updateInternal(brandId, productId, redTo20, DRAFT_ID);

        assertThat(optionQuantity(productId, red)).isEqualTo(16);
        assertThat(optionQuantity(productId, blue)).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 초안이 4건 동시에 도착 — 재고 변경은 한 번만 일어난다")
    void concurrentSameDraftAppliesOnce() throws Exception {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        data.stock(productId, 10);

        List<Callable<SellerProductUpdateResponse>> requests = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            requests.add(() -> sellerProductService.updateInternal(brandId, productId, stockTo(20), DRAFT_ID));
        }
        List<Outcome<SellerProductUpdateResponse>> outcomes = Concurrently.run(requests);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(data.stockQuantity(productId)).isEqualTo(20);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_change_logs WHERE product_id = ? AND idempotency_key = ?",
                Integer.class, productId, DRAFT_ID)).isEqualTo(1);
    }

    private int optionQuantity(long productId, long optionId) {
        return jdbc.queryForObject(
                "SELECT quantity FROM product_stock WHERE product_id = ? AND option_id = ?",
                Integer.class, productId, optionId);
    }
}
