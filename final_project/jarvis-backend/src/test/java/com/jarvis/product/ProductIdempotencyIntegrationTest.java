package com.jarvis.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/** I-10 상품 등록 멱등 키 (02 D48) — 같은 브랜드·같은 키의 상품은 하나만 저장된다 */
class ProductIdempotencyIntegrationTest extends MariaDbIntegrationTest {

    private static final String DRAFT_ID = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b";

    @Autowired ProductRepository productRepository;

    private Product newProduct(long brandId, long categoryId, String idempotencyKey) {
        return Product.create(brandId, categoryId, "새 상품", 15000, 12000, "img", null, null, null,
                ProductStatus.ON_SALE, idempotencyKey);
    }

    @Test
    @DisplayName("같은 초안을 6번 동시에 등록 — 1건만 저장되고 나머지는 제약 위반")
    void sameKeyStoresExactlyOneProduct() throws Exception {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long categoryId = data.category();

        List<Callable<Long>> attempts = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            attempts.add(() -> productRepository.saveAndFlush(newProduct(brandId, categoryId, DRAFT_ID)).getId());
        }
        List<Outcome<Long>> outcomes = Concurrently.run(attempts);

        assertThat(outcomes.stream().filter(Outcome::succeeded).count()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> !o.succeeded()).map(Outcome::error))
                .allMatch(DataIntegrityViolationException.class::isInstance);
        Long winner = outcomes.stream().filter(Outcome::succeeded).findFirst().orElseThrow().value();
        assertThat(productRepository.findByBrandIdAndIdempotencyKey(brandId, DRAFT_ID))
                .get().extracting(Product::getId).isEqualTo(winner);
    }

    @Test
    @DisplayName("키는 브랜드 범위 — 다른 브랜드는 같은 키를 쓸 수 있다")
    void keyIsScopedToBrand() {
        TestData data = new TestData(jdbc);
        long categoryId = data.category();
        long brandA = data.brand();
        long brandB = data.brand();

        Long a = productRepository.saveAndFlush(newProduct(brandA, categoryId, DRAFT_ID)).getId();
        Long b = productRepository.saveAndFlush(newProduct(brandB, categoryId, DRAFT_ID)).getId();

        assertThat(productRepository.findByBrandIdAndIdempotencyKey(brandA, DRAFT_ID))
                .get().extracting(Product::getId).isEqualTo(a);
        assertThat(productRepository.findByBrandIdAndIdempotencyKey(brandB, DRAFT_ID))
                .get().extracting(Product::getId).isEqualTo(b);
    }

    @Test
    @DisplayName("키 없는 상품은 제한 없이 쌓인다 — 크롤링 시드와 키를 안 보내는 등록은 영향이 없다")
    void productsWithoutKeyAreUnconstrained() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long categoryId = data.category();

        productRepository.saveAndFlush(newProduct(brandId, categoryId, null));
        productRepository.saveAndFlush(newProduct(brandId, categoryId, null));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product WHERE brand_id = ?", Integer.class, brandId))
                .isEqualTo(2);
    }
}
