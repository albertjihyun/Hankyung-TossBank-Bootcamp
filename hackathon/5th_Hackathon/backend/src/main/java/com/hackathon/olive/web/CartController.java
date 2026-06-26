package com.hackathon.olive.web;

import com.hackathon.olive.dto.CartDto.*;
import com.hackathon.olive.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public CartView get(@AuthenticationPrincipal Long userId) {
        return cartService.getCart(userId);
    }

    @GetMapping("/count")
    public Map<String, Long> count(@AuthenticationPrincipal Long userId) {
        return Map.of("count", cartService.count(userId));
    }

    @PostMapping
    public CartView add(@AuthenticationPrincipal Long userId, @Valid @RequestBody AddRequest req) {
        return cartService.add(userId, req);
    }

    @PatchMapping("/{itemId}")
    public CartView update(@AuthenticationPrincipal Long userId,
                           @PathVariable Long itemId,
                           @Valid @RequestBody UpdateRequest req) {
        return cartService.update(userId, itemId, req.quantity());
    }

    @DeleteMapping("/{itemId}")
    public CartView remove(@AuthenticationPrincipal Long userId, @PathVariable Long itemId) {
        return cartService.remove(userId, itemId);
    }

    @DeleteMapping
    public void clear(@AuthenticationPrincipal Long userId) {
        cartService.clear(userId);
    }
}
