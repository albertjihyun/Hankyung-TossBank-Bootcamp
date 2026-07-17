package com.jarvis.review;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Phase 2는 조회 전용(P-2 평점 집계·P-3 목록) — 작성/신고(M-1·M-3)는 Phase 4.
 * member_id NULL = 크롤링 리뷰, author_name 필수 (02 D19).
 */
@Entity
@Table(name = "review")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 회원 리뷰 자격 앵커, 아이템당 1개 (02 D4). 크롤링 리뷰는 NULL */
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "author_name", length = 50)
    private String authorName;

    /** DDL이 TINYINT라 ddl-auto=validate 통과용 JDBC 타입 명시 */
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewStatus status;
}
