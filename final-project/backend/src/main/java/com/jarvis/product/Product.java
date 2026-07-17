package com.jarvis.product;

import com.jarvis.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 도메인 경계를 넘는 참조(brand/category)는 객체 대신 id 보관 (03 §3-1).
 * 할인율·평점은 파생 계산 — 컬럼 없음 (02 D9·D15).
 */
@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "brand_id", nullable = false)
    private Long brandId;

    /** 소분류(leaf)만 참조 — 서비스·시드 검증 (02 D20·D26②) */
    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "original_price", nullable = false)
    private int originalPrice;

    @Column(nullable = false)
    private int price;

    /** 차감은 결제 성공 트랜잭션의 조건부 UPDATE — Phase 3 (02 D33) */
    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    /** 크롤링 시점 누적 판매량, 시드 후 불변 — 표시 판매량 = 이 값 + order_item 집계 (02 D18) */
    @Column(name = "base_sales_count", nullable = false)
    private int baseSalesCount;

    @Column(length = 500)
    private String summary;

    /** 키 축은 category.attribute_schema, 값은 자유 텍스트 JSON (02 D7·D11) */
    @Column(columnDefinition = "json")
    private String attributes;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    public boolean isPurchasable() {
        return status == ProductStatus.ON_SALE && stockQuantity > 0;
    }
}
