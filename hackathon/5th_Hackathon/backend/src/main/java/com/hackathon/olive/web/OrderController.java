package com.hackathon.olive.web;

import com.hackathon.olive.dto.OrderDto.*;
import com.hackathon.olive.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /** 결제(데모) → 주문 생성. */
    @PostMapping
    public OrderView checkout(@AuthenticationPrincipal Long userId,
                              @Valid @RequestBody CheckoutRequest req) {
        return orderService.checkout(userId, req);
    }

    @GetMapping
    public List<OrderView> list(@AuthenticationPrincipal Long userId) {
        return orderService.list(userId);
    }

    @GetMapping("/{id}")
    public OrderView get(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return orderService.get(userId, id);
    }
}
