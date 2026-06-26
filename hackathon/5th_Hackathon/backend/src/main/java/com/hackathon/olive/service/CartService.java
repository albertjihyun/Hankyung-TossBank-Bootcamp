package com.hackathon.olive.service;

import com.hackathon.olive.domain.CartItem;
import com.hackathon.olive.domain.Product;
import com.hackathon.olive.dto.CartDto.*;
import com.hackathon.olive.repository.CartItemRepository;
import com.hackathon.olive.repository.ProductRepository;
import com.hackathon.olive.util.PriceUtil;
import com.hackathon.olive.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public CartView getCart(Long userId) {
        List<CartItem> items = cartItemRepository.findByUserId(userId);
        if (items.isEmpty()) return new CartView(List.of(), 0, 0);

        // 상품 일괄 조회(N+1 방지)
        Map<Long, Product> products = productRepository
                .findAllById(items.stream().map(CartItem::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        List<ItemView> views = new ArrayList<>();
        int totalQty = 0, totalAmount = 0;
        for (CartItem ci : items) {
            Product p = products.get(ci.getProductId());
            if (p == null) continue; // 상품이 사라진 경우 스킵
            int finalPrice = PriceUtil.finalPrice(p);
            int lineTotal = finalPrice * ci.getQuantity();
            views.add(new ItemView(
                    ci.getId(), p.getId(), p.getName(), p.getImageUrl(),
                    p.getPrice(), p.getDiscountRate(), finalPrice,
                    ci.getQuantity(), p.getStock(), lineTotal, p.getStock() <= 0));
            totalQty += ci.getQuantity();
            totalAmount += lineTotal;
        }
        return new CartView(views, totalQty, totalAmount);
    }

    @Transactional
    public CartView add(Long userId, AddRequest req) {
        Product p = productRepository.findById(req.productId())
                .orElseThrow(() -> ApiException.notFound("상품을 찾을 수 없습니다."));
        if (p.getStock() <= 0) throw ApiException.badRequest("품절된 상품입니다.");

        CartItem item = cartItemRepository.findByUserIdAndProductId(userId, req.productId())
                .orElseGet(() -> new CartItem(userId, req.productId(), 0));
        int newQty = Math.min(item.getQuantity() + req.quantity(), p.getStock());
        item.setQuantity(newQty);
        cartItemRepository.save(item);
        return getCart(userId);
    }

    @Transactional
    public CartView update(Long userId, Long itemId, int quantity) {
        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> ApiException.notFound("장바구니 항목이 없습니다."));
        if (!item.getUserId().equals(userId)) throw ApiException.notFound("장바구니 항목이 없습니다.");
        Product p = productRepository.findById(item.getProductId())
                .orElseThrow(() -> ApiException.notFound("상품을 찾을 수 없습니다."));
        item.setQuantity(Math.max(1, Math.min(quantity, Math.max(1, p.getStock()))));
        cartItemRepository.save(item);
        return getCart(userId);
    }

    @Transactional
    public CartView remove(Long userId, Long itemId) {
        cartItemRepository.findById(itemId).ifPresent(item -> {
            if (item.getUserId().equals(userId)) cartItemRepository.delete(item);
        });
        return getCart(userId);
    }

    @Transactional
    public void clear(Long userId) {
        cartItemRepository.deleteByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long count(Long userId) {
        return cartItemRepository.countByUserId(userId);
    }
}
