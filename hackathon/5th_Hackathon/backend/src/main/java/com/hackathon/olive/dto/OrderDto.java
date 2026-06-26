package com.hackathon.olive.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderDto {
    private OrderDto() {}

    /** 장바구니 전체를 주문(데모: 결제 즉시 PAID). */
    public record CheckoutRequest(
            @NotBlank String recipientName,
            @NotBlank String phone,
            @NotBlank String address) {}

    public record ItemView(
            Long productId,
            String productName,
            String imageUrl,
            int unitPrice,
            int quantity) {}

    public record OrderView(
            Long id,
            String status,
            int totalAmount,
            String recipientName,
            String phone,
            String address,
            LocalDateTime createdAt,
            List<ItemView> items) {}
}
