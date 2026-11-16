package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.jarvis.product.Product;
import com.jarvis.product.ProductRepository;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * "판매량"의 정의를 실제 MariaDB에서 숫자로 고정한다 — 취소·반품된 아이템은 판매가 아니다.
 *
 * <p>주문 상태는 전량 취소일 때만 CANCELLED로 바뀐다(01 §2-1). 그래서 주문 상태(PAID)만 보고 세면
 * 부분 취소·반품분이 계속 판매로 잡혀, 판매자 화면의 판매량(S-3)과 매출 수량(S-1)이 서로 어긋나고
 * 전량 반품된 상품이 인기 순위에 남는다. 표시 판매량·인기순·매출 집계가 같은 숫자를 내야 한다.
 */
class SoldQuantityIntegrationTest extends MariaDbIntegrationTest {

    @Autowired OrderItemRepository orderItemRepository;
    @Autowired ProductRepository productRepository;

    private long item(TestData data, long orderId, long productId, String status, int quantity) {
        long itemId = data.orderItem(orderId, productId, status);
        jdbc.update("UPDATE order_item SET quantity = ? WHERE id = ?", quantity, itemId);
        return itemId;
    }

    @Test
    @DisplayName("한 주문에서 일부만 취소·반품 — 표시 판매량은 살아 있는 아이템만 센다")
    void displaySalesExcludeCancelledAndReturnedItems() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        long orderId = data.order(data.member(), "PAID");
        item(data, orderId, productId, "CONFIRMED", 3);
        item(data, orderId, productId, "SHIPPING", 2);
        item(data, orderId, productId, "CANCELLED", 5);
        item(data, orderId, productId, "RETURNED", 7);
        item(data, orderId, productId, "CANCEL_REQUESTED", 1);

        Map<Long, Long> sold = orderItemRepository.sumPaidQuantityByProduct(List.of(productId)).stream()
                .collect(Collectors.toMap(OrderItemRepository.ProductQuantityRow::getProductId,
                        OrderItemRepository.ProductQuantityRow::getQuantity));

        // 3 + 2 + 1(취소 '신청'은 아직 확정이 아니라 판매에 남는다) — 취소 5·반품 7은 빠진다
        assertThat(sold.get(productId)).isEqualTo(6L);
    }

    @Test
    @DisplayName("표시 판매량과 판매자 매출 수량은 같은 숫자다 — 한 화면의 두 지표가 어긋나지 않는다")
    void displaySalesMatchSellerSalesQuantity() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long productId = data.product(brandId);
        long orderId = data.order(data.member(), "PAID");
        item(data, orderId, productId, "DELIVERED", 4);
        item(data, orderId, productId, "RETURNED", 9);

        long display = orderItemRepository.sumPaidQuantityByProduct(List.of(productId)).get(0).getQuantity();
        long sales = orderItemRepository.sumSellerSales(brandId,
                LocalDateTime.now().minusDays(1), LocalDateTime.now().plusDays(1)).getQuantity();

        assertThat(display).isEqualTo(4L);
        assertThat(display).isEqualTo(sales);
    }

    @Test
    @DisplayName("전량 반품된 상품은 인기 순위에서 내려간다 — 주문이 PAID로 남아 있어도 판매로 세지 않는다")
    void fullyReturnedProductDropsOutOfPopularity() {
        TestData data = new TestData(jdbc);
        long brandId = data.brand();
        long returned = data.product(brandId);
        long sold = data.product(brandId);
        data.stock(returned, 10);
        data.stock(sold, 10);
        long memberId = data.member();
        item(data, data.order(memberId, "PAID"), returned, "RETURNED", 99);
        item(data, data.order(memberId, "PAID"), sold, "CONFIRMED", 1);

        // P-6 브랜드 인기순 — 99개 반품이 판매로 세졌다면 returned가 1위다
        List<Long> byPopularity = productRepository
                .findBrandProductsOrderByPopularity(brandId, null, PageRequest.of(0, 10))
                .map(Product::getId).getContent();
        // P-4 최근 판매 상위 — 반품뿐인 상품은 목록에 없다
        List<Long> recentTop = productRepository.findPopularIdsBySales(LocalDateTime.now().minusDays(7), 10);

        assertThat(byPopularity).containsExactly(sold, returned);
        assertThat(recentTop).containsExactly(sold);
    }

    @Test
    @DisplayName("결제 전·결제 실패 주문은 판매가 아니다")
    void unpaidOrdersAreNotSales() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        long memberId = data.member();
        item(data, data.order(memberId, "PENDING"), productId, "PENDING", 2);
        item(data, data.order(memberId, "PAYMENT_FAILED"), productId, "PENDING", 2);

        assertThat(orderItemRepository.sumPaidQuantityByProduct(List.of(productId))).isEmpty();
    }
}
