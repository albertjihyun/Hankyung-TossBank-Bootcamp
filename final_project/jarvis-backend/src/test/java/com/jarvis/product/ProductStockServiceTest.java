package com.jarvis.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.product.ProductStockService.StockKey;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 차감 단위가 (상품, 옵션)이다 — 02 D33 개정 */
@ExtendWith(MockitoExtension.class)
class ProductStockServiceTest {

    private static final StockKey BLACK = new StockKey(10L, 100L);
    private static final StockKey WHITE = new StockKey(10L, 101L);
    private static final StockKey NO_OPTION = new StockKey(20L, null);

    @Mock ProductStockRepository productStockRepository;
    @Mock ProductChangeLogRepository productChangeLogRepository;

    @Captor ArgumentCaptor<List<ProductChangeLog>> logsCaptor;

    @InjectMocks ProductStockService productStockService;

    @Test
    @DisplayName("전 품목 차감 성공 — true, 재고 0 도달분만 품절 로그(어느 옵션인지 함께)")
    void deductsAllAndLogsOnlySoldOut() {
        when(productStockRepository.deduct(anyLong(), any(), anyInt())).thenReturn(1);
        when(productStockRepository.findQuantity(10L, 100L)).thenReturn(Optional.of(0));
        when(productStockRepository.findQuantity(20L, null)).thenReturn(Optional.of(98));

        boolean result = productStockService.deduct(Map.of(BLACK, 1, NO_OPTION, 2));

        assertThat(result).isTrue();
        verify(productChangeLogRepository).saveAll(logsCaptor.capture());
        assertThat(logsCaptor.getValue()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getProductId()).isEqualTo(10L);
                    assertThat(log.getOptionId()).isEqualTo(100L);
                    assertThat(log.getChangeType()).isEqualTo(ProductChangeType.STOCK);
                    assertThat(log.getNewValue()).isEqualTo("0");
                });
    }

    // ---- 취소/반품 복원 (2026-08-10 — 구 "MVP 미구현") ----

    @Test
    @DisplayName("복원으로 재고가 0에서 풀리면 재입고 로그 1행 — 차감의 품절 로그와 대칭 (02 D32)")
    void logsRestockWhenStockLeavesZero() {
        when(productStockRepository.restore(10L, 100L, 2)).thenReturn(1);
        // 복원 결과가 복원량과 같다 = 직전이 0이었다
        when(productStockRepository.findQuantity(10L, 100L)).thenReturn(Optional.of(2));

        productStockService.restore(Map.of(BLACK, 2));

        verify(productChangeLogRepository).saveAll(logsCaptor.capture());
        assertThat(logsCaptor.getValue()).singleElement()
                .satisfies(log -> {
                    assertThat(log.getProductId()).isEqualTo(10L);
                    assertThat(log.getOptionId()).isEqualTo(100L);
                    assertThat(log.getChangeType()).isEqualTo(ProductChangeType.STOCK);
                    assertThat(log.getOldValue()).isEqualTo("0");
                    assertThat(log.getNewValue()).isEqualTo("2");
                });
    }

    @Test
    @DisplayName("품절이 아니었던 재고의 복원은 로그를 쓰지 않는다 — 주문에 의한 증감은 미기록 (02 D32)")
    void noLogWhenStockWasNotZero() {
        when(productStockRepository.restore(10L, 100L, 2)).thenReturn(1);
        when(productStockRepository.findQuantity(10L, 100L)).thenReturn(Optional.of(7));

        productStockService.restore(Map.of(BLACK, 2));

        verify(productChangeLogRepository, never()).saveAll(anyList());
        verify(productChangeLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("재고 행이 사라진 상품은 건너뛴다 — 취소·반품 자체를 막지는 않는다")
    void skipsMissingStockRow() {
        when(productStockRepository.restore(20L, null, 1)).thenReturn(0);

        productStockService.restore(Map.of(NO_OPTION, 1));

        verify(productStockRepository, never()).findQuantity(anyLong(), any());
        verify(productChangeLogRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("복원도 차감과 같은 순서로 잠근다 — 취소 배치와 결제가 엇갈려도 데드락이 없게")
    void restoreLocksInAscendingKeyOrder() {
        Map<StockKey, Integer> shuffled = new LinkedHashMap<>();
        shuffled.put(WHITE, 1);
        shuffled.put(new StockKey(10L, null), 1);
        shuffled.put(BLACK, 1);
        when(productStockRepository.restore(anyLong(), any(), anyInt())).thenReturn(1);
        lenient().when(productStockRepository.findQuantity(anyLong(), any()))
                .thenReturn(Optional.of(5));

        productStockService.restore(shuffled);

        InOrder order = inOrder(productStockRepository);
        order.verify(productStockRepository).restore(10L, null, 1);
        order.verify(productStockRepository).restore(10L, 100L, 1);
        order.verify(productStockRepository).restore(10L, 101L, 1);
    }

}
