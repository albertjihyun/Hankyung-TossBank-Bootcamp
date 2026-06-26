package com.hackathon.olive.service;

import com.hackathon.olive.domain.*;
import com.hackathon.olive.dto.OrderDto.*;
import com.hackathon.olive.repository.CartItemRepository;
import com.hackathon.olive.repository.OrderRepository;
import com.hackathon.olive.repository.ProductRepository;
import com.hackathon.olive.util.PriceUtil;
import com.hackathon.olive.web.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    /**
     * 장바구니 → 주문 생성(데모: 결제 즉시 PAID).
     * 재고 차감 + 주문시점 스냅샷 저장 + 장바구니 비우기.
     */
    @Transactional
    public OrderView checkout(Long userId, CheckoutRequest req) {
        List<CartItem> cart = cartItemRepository.findByUserId(userId);
        if (cart.isEmpty()) throw ApiException.badRequest("장바구니가 비어 있습니다.");

        Map<Long, Product> products = productRepository
                .findAllById(cart.stream().map(CartItem::getProductId).toList())
                .stream().collect(Collectors.toMap(Product::getId, p -> p));

        Order order = new Order();
        order.setUserId(userId);
        order.setStatus("PAID");
        order.setRecipientName(req.recipientName());
        order.setPhone(req.phone());
        order.setAddress(req.address());

        int total = 0;
        for (CartItem ci : cart) {
            Product p = products.get(ci.getProductId());
            if (p == null) throw ApiException.badRequest("주문할 수 없는 상품이 포함되어 있습니다.");
            if (p.getStock() < ci.getQuantity()) {
                throw ApiException.badRequest("재고가 부족한 상품이 있습니다: " + p.getName());
            }
            int unitPrice = PriceUtil.finalPrice(p);

            OrderItem oi = new OrderItem();
            oi.setProductId(p.getId());
            oi.setProductName(p.getName());
            oi.setImageUrl(p.getImageUrl());
            oi.setUnitPrice(unitPrice);
            oi.setQuantity(ci.getQuantity());
            order.addItem(oi);

            p.setStock(p.getStock() - ci.getQuantity()); // 재고 차감(영속 상태라 dirty checking)
            total += unitPrice * ci.getQuantity();
        }
        order.setTotalAmount(total);

        orderRepository.save(order);
        cartItemRepository.deleteByUserId(userId);
        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> list(Long userId) {
        return orderRepository.findByUserIdOrderByIdDesc(userId).stream()
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public OrderView get(Long userId, Long orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> ApiException.notFound("주문을 찾을 수 없습니다."));
        if (!order.getUserId().equals(userId)) throw ApiException.notFound("주문을 찾을 수 없습니다.");
        return toView(order);
    }

    private OrderView toView(Order o) {
        List<ItemView> items = o.getItems().stream()
                .map(i -> new ItemView(i.getProductId(), i.getProductName(),
                        i.getImageUrl(), i.getUnitPrice(), i.getQuantity()))
                .toList();
        return new OrderView(o.getId(), o.getStatus(), o.getTotalAmount(),
                o.getRecipientName(), o.getPhone(), o.getAddress(), o.getCreatedAt(), items);
    }
}
