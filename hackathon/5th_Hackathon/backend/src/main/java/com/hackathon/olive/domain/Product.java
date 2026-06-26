package com.hackathon.olive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_master_category", columnList = "master_category"),
        @Index(name = "idx_article_type", columnList = "article_type")
})
@Getter
@Setter
@NoArgsConstructor
public class Product {

    // styles.csv id를 그대로 사용하므로 자동 생성 금지
    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private String gender;

    @Column(name = "master_category", length = 50)
    private String masterCategory;

    @Column(name = "sub_category", length = 50)
    private String subCategory;

    @Column(name = "article_type", length = 50)
    private String articleType;

    @Column(name = "base_colour", length = 30)
    private String baseColour;

    @Column(length = 20)
    private String season;

    @Column(nullable = false)
    private int price;

    @Column(name = "discount_rate", nullable = false)
    private int discountRate;

    @Column(nullable = false)
    private int stock;

    @Column(name = "image_url", length = 512, nullable = false)
    private String imageUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
