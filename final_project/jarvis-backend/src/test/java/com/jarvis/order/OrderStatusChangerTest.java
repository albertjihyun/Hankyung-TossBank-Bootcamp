package com.jarvis.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.product.ProductStockService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 상태 전이 로그의 해상도 규칙 (01 §6.5 규칙 4, 2026-08-06 개정) —
 * 아이템 상태 전이는 아이템마다 1행(orderItemId 필수), 주문 상태 전이만 null.
 */
@ExtendWith(MockitoExtension.class)
class OrderStatusChangerTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ClaimRepository claimRepository;
    @Mock private OrderStatusLogRepository logRepository;
    @Mock private ProductStockService productStockService;

    @InjectMocks private OrderStatusChanger changer;

    private static OrderItem item(long id, long orderId) {
        OrderItem i = mock(OrderItem.class);
        lenient().when(i.getId()).thenReturn(id);
        lenient().when(i.getOrderId()).thenReturn(orderId);
        return i;
    }

    /** 재고 복원 대상 정보까지 갖춘 아이템 — 복원 키는 optionName 스냅샷이 아니라 optionId다 (02 D33) */
    private static OrderItem item(long id, long orderId, Long productId, Long optionId, int quantity) {
        OrderItem i = item(id, orderId);
        lenient().when(i.getProductId()).thenReturn(productId);
        lenient().when(i.getOptionId()).thenReturn(optionId);
        lenient().when(i.getQuantity()).thenReturn(quantity);
        return i;
    }

    private static Claim returnClaim(long id, long orderItemId) {
        Claim c = mock(Claim.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getOrderItemId()).thenReturn(orderItemId);
        lenient().when(c.requestedItemStatus()).thenReturn(OrderItemStatus.RETURN_REQUESTED);
        lenient().when(c.completedItemStatus()).thenReturn(OrderItemStatus.RETURNED);
        return c;
    }

    private static Claim claim(long id, long orderItemId, String reason) {
        Claim c = mock(Claim.class);
        lenient().when(c.getId()).thenReturn(id);
        lenient().when(c.getOrderItemId()).thenReturn(orderItemId);
        lenient().when(c.getReason()).thenReturn(reason);
        lenient().when(c.requestedItemStatus()).thenReturn(OrderItemStatus.CANCEL_REQUESTED);
        lenient().when(c.completedItemStatus()).thenReturn(OrderItemStatus.CANCELLED);
        return c;
    }

    private List<OrderStatusLog> savedLogs(int expectedCount) {
        ArgumentCaptor<OrderStatusLog> captor = ArgumentCaptor.forClass(OrderStatusLog.class);
        verify(logRepository, times(expectedCount)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    @DisplayName("배송 전이: 같은 주문의 아이템 2개면 로그도 2행이고 각자 orderItemId를 갖는다")
    void shipmentLogsPerItem() {
        List<OrderItem> candidates = List.of(item(5551L, 500L), item(5552L, 500L));

        when(orderItemRepository.findAllByStatusAndStatusChangedAtLessThanEqual(
                eq(OrderItemStatus.SHIPPING), any(), any())).thenReturn(candidates);
        when(orderItemRepository.transitionStatus(anyLong(), any(), any(), any())).thenReturn(1);

        int count = changer.transitionShipment(OrderItemStatus.SHIPPING, OrderItemStatus.DELIVERED,
                LocalDateTime.now(), true);

        assertThat(count).isEqualTo(2);
        List<OrderStatusLog> logs = savedLogs(2);
        assertThat(logs).allSatisfy(l -> {
            assertThat(l.getOrderId()).isEqualTo(500L);
            assertThat(l.getToStatus()).isEqualTo("DELIVERED");
            assertThat(l.getActorType()).isEqualTo(ActorType.SYSTEM);
        });
        assertThat(logs).extracting(OrderStatusLog::getOrderItemId)
                .containsExactly(5551L, 5552L); // 구 규칙이었다면 주문 단위 1행이었다
    }

    @Test
    @DisplayName("배송 전이: 조건부 UPDATE가 0건이면 그 아이템 로그도 남지 않는다")
    void shipmentSkipsLostRace() {
        List<OrderItem> candidates = List.of(item(5551L, 500L), item(5552L, 500L));

        when(orderItemRepository.findAllByStatusAndStatusChangedAtLessThanEqual(
                eq(OrderItemStatus.SHIPPING), any(), any())).thenReturn(candidates);
        when(orderItemRepository.transitionStatus(eq(5551L), any(), any(), any())).thenReturn(1);
        when(orderItemRepository.transitionStatus(eq(5552L), any(), any(), any())).thenReturn(0);

        int count = changer.transitionShipment(OrderItemStatus.SHIPPING, OrderItemStatus.DELIVERED,
                LocalDateTime.now(), true);

        assertThat(count).isEqualTo(1);
        assertThat(savedLogs(1).get(0).getOrderItemId()).isEqualTo(5551L);
    }

    @Test
    @DisplayName("CONFIRMED 전이는 writeLog=false라 로그가 없다 (01 §6.5 규칙 2)")
    void confirmedWritesNoLog() {
        List<OrderItem> candidates = List.of(item(5551L, 500L));

        when(orderItemRepository.findAllByStatusAndStatusChangedAtLessThanEqual(
                eq(OrderItemStatus.DELIVERED), any(), any())).thenReturn(candidates);
        when(orderItemRepository.transitionStatus(anyLong(), any(), any(), any())).thenReturn(1);

        changer.transitionShipment(OrderItemStatus.DELIVERED, OrderItemStatus.CONFIRMED,
                LocalDateTime.now(), false);

        savedLogs(0);
    }

    /** 승인 대기 중인 클레임과 그 아이템을 리포지토리에 심는다 */
    private void stubRequested(Claim claim, OrderItem item) {
        lenient().when(claim.getStatus()).thenReturn(ClaimStatus.REQUESTED);
        when(claimRepository.findById(claim.getId())).thenReturn(java.util.Optional.of(claim));
        when(orderItemRepository.findById(item.getId())).thenReturn(java.util.Optional.of(item));
    }

    @Test
    @DisplayName("클레임 승인: 아이템 1행 로그에 그 클레임의 reason이 남고 actor는 신청자(USER)다")
    void claimApprovalLogsItemWithOwnReason() {
        Claim claim = claim(2L, 5552L, "배송지연");
        stubRequested(claim, item(5552L, 500L, 10L, null, 1));
        when(orderItemRepository.transitionStatus(eq(5552L), any(), any(), any())).thenReturn(1);
        // 전량 취소 승격 경로는 이 테스트 관심 밖 — 남은 아이템이 있는 것으로 둔다
        when(orderItemRepository.countByOrderIdAndStatusNot(eq(500L), any())).thenReturn(1L);

        boolean approved = changer.approveClaim(2L, LocalDateTime.now());

        assertThat(approved).isTrue();
        verify(claim).approve(any());
        List<OrderStatusLog> logs = savedLogs(1);
        assertThat(logs.get(0).getOrderItemId()).isEqualTo(5552L);
        assertThat(logs.get(0).getReason()).isEqualTo("배송지연");
        assertThat(logs.get(0).getActorType()).isEqualTo(ActorType.USER);
    }

    @Test
    @DisplayName("클레임 승인: 취소 확정분의 재고를 같은 트랜잭션에서 되돌린다 — 복원 키는 (상품, 옵션)")
    void cancelApprovalRestoresStock() {
        Claim claim = claim(1L, 5551L, "단순변심");
        stubRequested(claim, item(5551L, 500L, 10L, 100L, 2));
        when(orderItemRepository.transitionStatus(eq(5551L), any(), any(), any())).thenReturn(1);
        when(orderItemRepository.countByOrderIdAndStatusNot(eq(500L), any())).thenReturn(1L);

        changer.approveClaim(1L, LocalDateTime.now());

        verify(productStockService).restore(Map.of(new ProductStockService.StockKey(10L, 100L), 2));
    }

    @Test
    @DisplayName("클레임 승인: 반품도 재고 복원 대상이다 — 옵션 없는 상품은 optionId가 null")
    void returnApprovalRestoresStock() {
        Claim claim = returnClaim(2L, 5552L);
        stubRequested(claim, item(5552L, 500L, 20L, null, 3));
        when(orderItemRepository.transitionStatus(eq(5552L), any(), any(), any())).thenReturn(1);

        changer.approveClaim(2L, LocalDateTime.now());

        java.util.Map<ProductStockService.StockKey, Integer> expected = new java.util.HashMap<>();
        expected.put(new ProductStockService.StockKey(20L, null), 3);
        verify(productStockService).restore(expected);
        // 반품은 주문 취소 승격 대상이 아니다
        verify(orderItemRepository, never()).countByOrderIdAndStatusNot(anyLong(), any());
    }

    @Test
    @DisplayName("클레임 승인: 전이 경합에 지면 아무것도 바꾸지 않는다 — 재고 이중 복원이 없다")
    void lostRaceChangesNothing() {
        Claim claim = claim(1L, 5551L, "단순변심");
        stubRequested(claim, item(5551L, 500L, 10L, 100L, 2));
        when(orderItemRepository.transitionStatus(anyLong(), any(), any(), any())).thenReturn(0);

        assertThat(changer.approveClaim(1L, LocalDateTime.now())).isFalse();

        verify(claim, never()).approve(any());
        verify(productStockService, never()).restore(any());
        verify(logRepository, never()).save(any());
    }

    @Test
    @DisplayName("클레임 승인: 이미 처리된 클레임은 다시 승인하지 않는다 — 전이도 시도하지 않는다")
    void alreadyProcessedClaimIsSkipped() {
        Claim claim = claim(1L, 5551L, "단순변심");
        when(claim.getStatus()).thenReturn(ClaimStatus.COMPLETED);
        when(claimRepository.findById(1L)).thenReturn(java.util.Optional.of(claim));

        assertThat(changer.approveClaim(1L, LocalDateTime.now())).isFalse();

        verify(orderItemRepository, never()).transitionStatus(anyLong(), any(), any(), any());
        verify(productStockService, never()).restore(any());
    }

    @Test
    @DisplayName("자동 승인 대상 조회는 건수를 묶는다 — 밀린 클레임 전부를 한 번에 올리지 않는다")
    void dueClaimLookupIsBounded() {
        when(claimRepository.findDueIds(eq(ClaimStatus.REQUESTED), any(), any())).thenReturn(List.of(1L, 2L));

        assertThat(changer.findDueClaimIds(LocalDateTime.now(), 200)).containsExactly(1L, 2L);

        ArgumentCaptor<org.springframework.data.domain.Pageable> page =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(claimRepository).findDueIds(eq(ClaimStatus.REQUESTED), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    @DisplayName("배송 전이 후보 조회도 건수를 묶는다")
    void shipmentCandidateLookupIsBounded() {
        when(orderItemRepository.findAllByStatusAndStatusChangedAtLessThanEqual(any(), any(), any()))
                .thenReturn(List.of());

        changer.transitionShipment(OrderItemStatus.SHIPPING, OrderItemStatus.DELIVERED, LocalDateTime.now(), true);

        ArgumentCaptor<org.springframework.data.domain.Pageable> page =
                ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        verify(orderItemRepository).findAllByStatusAndStatusChangedAtLessThanEqual(any(), any(), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(OrderStatusChanger.SHIPMENT_BATCH_SIZE);
    }
}
