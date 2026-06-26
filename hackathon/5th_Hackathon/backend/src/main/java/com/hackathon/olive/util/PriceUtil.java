package com.hackathon.olive.util;

import com.hackathon.olive.domain.Product;

/** 할인가 계산을 한 곳에서. 프런트(discountedPrice)와 동일 규칙(100원 단위 반올림). */
public final class PriceUtil {
    private PriceUtil() {}

    public static int finalPrice(Product p) {
        if (p.getDiscountRate() <= 0) return p.getPrice();
        long discounted = (long) p.getPrice() * (100 - p.getDiscountRate()) / 100;
        return (int) (Math.round(discounted / 100.0) * 100);
    }
}
