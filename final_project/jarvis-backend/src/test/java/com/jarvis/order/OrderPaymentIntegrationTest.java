package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.cart.CartService;
import com.jarvis.order.dto.OrderCreateRequest;
import com.jarvis.order.PaymentService.PaymentResult;
import com.jarvis.order.dto.OrderCreateResponse;
import com.jarvis.product.ProductStockService;
import com.jarvis.recommendation.ConversionAttribution;
import com.jarvis.recommendation.RecommendationAttributionResolver;
import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 주문 생성의 "재고 확보 → 결제" 순서를 실제 MariaDB에서 확인한다 (01 §2-1, 2026-11-17).
 *
 * <p>예전 순서(결제 → 차감)에서는 마지막 1개를 두 명이 동시에 주문하면 <b>둘 다 결제 승인을 받고</b> 한 명만
 * 재고를 얻었다. 진 쪽은 "결제 실패"로 표시되지만 승인은 이미 난 뒤다 — 취소 수단이 없어 실제 결제사였다면
 * 돈만 빠져나간다. 결제 호출 횟수를 세어 그 일이 없음을 본다.
 */
@Import({OrderService.class, OrderStatusChanger.class, ProductStockService.class})
class OrderPaymentIntegrationTest extends MariaDbIntegrationTest {

    @Autowired OrderService orderService;

    @MockitoBean PaymentService paymentService;
    @MockitoBean OrderEventRecorder orderEventRecorder;
    @MockitoBean CartService cartService;
    @MockitoBean RecommendationAttributionResolver attributionResolver;

    @BeforeEach
    void setUp() {
        when(attributionResolver.resolveForConversion(any(), any(), any(), any()))
                .thenReturn(ConversionAttribution.NONE);
    }

    private static OrderCreateRequest buy(long productId, String paymentMethod) {
        return new OrderCreateRequest(null,
                List.of(new OrderCreateRequest.OrderLine(productId, null, 1, null)), null,
                new OrderCreateRequest.AddressInput("김자비", "010-1234-5678", "06236", "서울시 강남구", null),
                null, paymentMethod);
    }

    private int stockLogs(long productId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_change_logs WHERE product_id = ? AND change_type = 'STOCK'",
                Integer.class, productId);
    }

    @Test
    @DisplayName("마지막 1개를 두 명이 동시에 주문 — 한 명만 사고, 결제는 딱 한 번만 시도된다")
    void lastItemIsPaidForExactlyOnce() throws Exception {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 1);
        long alice = data.member();
        long bob = data.member();
        when(paymentService.pay(anyString(), anyInt())).thenReturn(PaymentResult.approved());

        List<Outcome<OrderCreateResponse>> outcomes = Concurrently.run(List.of(
                () -> orderService.create(alice, buy(productId, "MOCK_CARD")),
                () -> orderService.create(bob, buy(productId, "MOCK_CARD"))));

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes).extracting(o -> o.value().status())
                .containsExactlyInAnyOrder("PAID", "PAYMENT_FAILED");
        assertThat(outcomes).filteredOn(o -> o.value().status().equals("PAYMENT_FAILED"))
                .extracting(o -> o.value().failureReason()).containsExactly("OUT_OF_STOCK");
        assertThat(data.stockQuantity(productId)).isZero();
        // 핵심 — 재고를 못 잡은 쪽은 결제를 시도조차 하지 않았다. 예전 순서였다면 2번이다
        verify(paymentService, times(1)).pay(anyString(), anyInt());
    }

    @Test
    @DisplayName("결제 거절 — 잡았던 재고가 그대로 돌아오고, 품절·재입고 로그가 남지 않는다")
    void declinedPaymentReleasesStockWithoutTrace() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 1);
        when(paymentService.pay(anyString(), anyInt())).thenReturn(PaymentResult.declined("MOCK_DECLINED"));

        OrderCreateResponse response = orderService.create(data.member(), buy(productId, "MOCK_FAIL"));

        assertThat(response.status()).isEqualTo("PAYMENT_FAILED");
        assertThat(response.failureReason()).isEqualTo("MOCK_DECLINED");
        assertThat(data.stockQuantity(productId)).as("거절된 주문이 재고를 쥐고 있지 않다").isEqualTo(1);
        // 마지막 1개를 잡았다 놓았으니 수량은 0을 찍고 돌아왔다 — 그걸 품절 신호로 남기면 I-15가 속는다
        assertThat(stockLogs(productId)).isZero();
    }

    @Test
    @DisplayName("결제 성공으로 품절 — 그때에야 품절 로그 1건이 남는다")
    void successfulPurchaseLogsSoldOut() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 1);
        when(paymentService.pay(anyString(), anyInt())).thenReturn(PaymentResult.approved());

        OrderCreateResponse response = orderService.create(data.member(), buy(productId, "MOCK_CARD"));

        assertThat(response.status()).isEqualTo("PAID");
        assertThat(data.stockQuantity(productId)).isZero();
        assertThat(stockLogs(productId)).isEqualTo(1);
    }

    @Test
    @DisplayName("결제 호출이 예외로 끝남 — 주문도 재고 차감도 남지 않는다(트랜잭션이 통째로 되돌린다)")
    void paymentExceptionRollsEverythingBack() {
        TestData data = new TestData(jdbc);
        long productId = data.product();
        data.stock(productId, 3);
        long memberId = data.member();
        when(paymentService.pay(anyString(), anyInt())).thenThrow(new IllegalStateException("결제사 연결 실패"));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> orderService.create(memberId, buy(productId, "MOCK_CARD")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(data.stockQuantity(productId)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders WHERE member_id = ?", Integer.class, memberId))
                .isZero();
    }
}
