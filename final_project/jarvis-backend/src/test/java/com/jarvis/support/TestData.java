package com.jarvis.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * 통합 테스트용 최소 행 삽입기. 엔티티 팩토리를 거치지 않고 SQL로 넣는다 — 검증 대상이 "DB가 무엇을
 * 받아들이고 거부하는가"라서, 앱 계층의 검증을 우회해 스키마에 직접 닿아야 하는 경우가 있다.
 * NOT NULL 컬럼만 채운다.
 */
public final class TestData {

    private final JdbcTemplate jdbc;
    private int sequence;

    public TestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long member() {
        int n = ++sequence;
        return insert("INSERT INTO member (email, password, nickname, role, gender, birth_date, "
                + "agreed_terms_at, agreed_privacy_at, created_at) "
                + "VALUES (?, 'pw', ?, 'USER', 'FEMALE', '2000-01-01', NOW(), NOW(), NOW())",
                "user" + n + "@test.local", "user" + n);
    }

    public long brand() {
        return insert("INSERT INTO brand (name, created_at) VALUES (?, NOW())", "brand" + (++sequence));
    }

    public long category() {
        return insert("INSERT INTO category (name, created_at) VALUES (?, NOW())", "category" + (++sequence));
    }

    /** 판매 중인 상품 한 건 — 브랜드·카테고리도 함께 만든다 */
    public long product() {
        return product(brand());
    }

    public long product(long brandId) {
        return insert("INSERT INTO product (brand_id, category_id, name, original_price, price, image_url, "
                + "status, created_at, updated_at) VALUES (?, ?, ?, 15000, 12000, 'img', 'ON_SALE', NOW(), NOW())",
                brandId, category(), "product" + (++sequence));
    }

    public long option(long productId, String name) {
        return insert("INSERT INTO product_option (product_id, name, created_at) VALUES (?, ?, NOW())",
                productId, name);
    }

    /** 옵션 없는 상품의 재고 행 */
    public long stock(long productId, int quantity) {
        return insert("INSERT INTO product_stock (product_id, option_id, quantity, created_at) "
                + "VALUES (?, NULL, ?, NOW())", productId, quantity);
    }

    public long stock(long productId, long optionId, int quantity) {
        return insert("INSERT INTO product_stock (product_id, option_id, quantity, created_at) "
                + "VALUES (?, ?, ?, NOW())", productId, optionId, quantity);
    }

    public long order(long memberId, String status) {
        return insert("INSERT INTO orders (member_id, status, payment_method, total_amount, recipient, phone, "
                + "zip_code, address1, paid_at, created_at) "
                + "VALUES (?, ?, 'MOCK_CARD', 12000, 'r', 'p', 'z', 'a1', NOW(), NOW())", memberId, status);
    }

    public long orderItem(long orderId, long productId, String status) {
        return insert("INSERT INTO order_item (order_id, product_id, product_name, price, original_price, "
                + "quantity, status, status_changed_at, created_at) "
                + "VALUES (?, ?, 'p', 12000, 15000, 1, ?, NOW(), NOW())", orderId, productId, status);
    }

    public int stockQuantity(long productId) {
        return jdbc.queryForObject(
                "SELECT quantity FROM product_stock WHERE product_id = ? AND option_id IS NULL",
                Integer.class, productId);
    }

    private long insert(String sql, Object... args) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keys);
        return keys.getKey().longValue();
    }
}
