package com.hackathon.olive.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class CartDto {
    private CartDto() {}

    public record AddRequest(
            @NotNull Long productId,
            @Min(1) int quantity) {}

    public record UpdateRequest(@Min(1) int quantity) {}

    public record ItemView(
            Long id,
            Long productId,
            String name,
            String imageUrl,
            int price,
            int discountRate,
            int finalPrice,
            int quantity,
            int stock,
            int lineTotal,
            boolean soldOut) {}

    public record CartView(List<ItemView> items, int totalQuantity, int totalAmount) {}
}
