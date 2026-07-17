package com.jarvis.review;

import com.jarvis.global.response.ApiResponse;
import com.jarvis.review.dto.ReviewListResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** P-3 (04 §2) — 상품 후기 목록. 작성/신고는 Phase 4 */
@RestController
@RequiredArgsConstructor
@Validated
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/api/products/{productId}/reviews")
    public ApiResponse<ReviewListResponse> productReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "latest") @Pattern(regexp = "latest|rating") String sort) {
        return ApiResponse.success(reviewService.getProductReviews(productId, page, size, sort));
    }
}
