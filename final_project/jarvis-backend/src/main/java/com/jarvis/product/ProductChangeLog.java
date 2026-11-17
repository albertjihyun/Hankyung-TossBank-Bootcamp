package com.jarvis.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 상품 변경 로그 (02 D32) — append-only, FK 미설정. 전후 값 동일 시 미기록.
 * 주문에 의한 재고 -1은 미기록 — 품절 전환(new_value=0)·수동 조정만 기록.
 */
@Entity
@Table(name = "product_change_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    /**
     * STOCK 로그에서 어느 옵션인지 (02 D33 개정). 없으면 "재고 100 → 50"이 어느 옵션 얘긴지
     * 판매자가 알 수 없다. PRICE·STATUS는 상품 단위라 null이고, 옵션 없는 상품도 null이다.
     */
    @Column(name = "option_id")
    private Long optionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", nullable = false)
    private ProductChangeType changeType;

    @Column(name = "old_value", length = 50)
    private String oldValue;

    @Column(name = "new_value", length = 50)
    private String newValue;

    /**
     * 이 변경을 일으킨 I-11 요청의 `Idempotency-Key`(AI의 draftId). 재고 변경에만 채운다 (02 D52).
     * 재고는 절대값으로 덮어쓰는데 그 사이 주문이 수량을 줄이므로, 같은 요청이 한 번 더 적용되면
     * 팔린 수량이 되살아난다 — 이 값으로 "이미 적용한 요청"을 알아본다.
     */
    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private LocalDateTime createdAt;

    /** 상품 단위 변경(PRICE·STATUS) — 옵션 개념이 없다 */
    public static ProductChangeLog of(Long productId, ProductChangeType changeType,
                                      String oldValue, String newValue) {
        return ofOption(productId, null, changeType, oldValue, newValue);
    }

    /** 재고 변경(STOCK) — 옵션 없는 상품이면 optionId가 null이다 */
    public static ProductChangeLog ofOption(Long productId, Long optionId,
                                            ProductChangeType changeType,
                                            String oldValue, String newValue) {
        return ofOption(productId, optionId, changeType, oldValue, newValue, null);
    }

    public static ProductChangeLog ofOption(Long productId, Long optionId,
                                            ProductChangeType changeType,
                                            String oldValue, String newValue, String idempotencyKey) {
        ProductChangeLog log = new ProductChangeLog();
        log.productId = productId;
        log.optionId = optionId;
        log.changeType = changeType;
        log.oldValue = oldValue;
        log.newValue = newValue;
        log.idempotencyKey = idempotencyKey;
        log.createdAt = LocalDateTime.now();
        return log;
    }
}
