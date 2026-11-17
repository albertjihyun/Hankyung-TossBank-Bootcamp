-- 금액·수량 CHECK 제약 (02 D51) — 서비스 계층에만 있던 검증을 DB가 한 번 더 막는다.
--
-- 적용 시점: **앱 배포와 무관.** 앱은 이미 같은 규칙을 지키고 있어(D26·D28) 제약이 생겨도 정상 요청은
-- 영향이 없다. 앱 코드 변경도 없다.
--
-- ⚠️ 적용 전 확인: 기존 데이터에 위반 행이 있으면 ALTER가 실패한다. 아래 쿼리가 전부 0이어야 한다.
--   SELECT COUNT(*) FROM product    WHERE price < 0 OR original_price < 0 OR price > original_price OR base_sales_count < 0;
--   SELECT COUNT(*) FROM orders     WHERE total_amount < 0;
--   SELECT COUNT(*) FROM order_item WHERE price < 0 OR original_price < 0 OR quantity <= 0;
-- 0이 아니면 그 행을 먼저 바로잡는다 — 제약을 느슨하게 고쳐 넣지 않는다.
--
-- 재실행 무해: IF NOT EXISTS.
-- 되돌리기: ALTER TABLE <table> DROP CONSTRAINT <name>;

ALTER TABLE product
    ADD CONSTRAINT IF NOT EXISTS chk_product_price
        CHECK (price >= 0 AND original_price >= 0 AND price <= original_price),
    ADD CONSTRAINT IF NOT EXISTS chk_product_base_sales CHECK (base_sales_count >= 0);

ALTER TABLE orders
    ADD CONSTRAINT IF NOT EXISTS chk_orders_total_amount CHECK (total_amount >= 0);

ALTER TABLE order_item
    ADD CONSTRAINT IF NOT EXISTS chk_order_item_amount CHECK (price >= 0 AND original_price >= 0),
    ADD CONSTRAINT IF NOT EXISTS chk_order_item_quantity CHECK (quantity > 0);
