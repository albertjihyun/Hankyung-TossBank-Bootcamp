-- 주문 멱등 키 (02 D47) — O-1 `Idempotency-Key` 헤더를 저장해 같은 구매 의도의 재전송을 한 주문으로 묶는다.
--
-- 적용 시점: **앱 배포 전.** 새 앱은 `orders.idempotency_key` 컬럼이 없으면 `ddl-auto=validate`에 걸려
-- 기동을 거부한다. 컬럼은 NULL 허용이라 구 앱과 무해하게 공존한다(구 앱은 이 컬럼을 쓰지 않는다).
--
-- 재실행 무해: 컬럼·인덱스 모두 IF NOT EXISTS.
-- 되돌리기: ALTER TABLE orders DROP INDEX uk_orders_idempotency, DROP COLUMN idempotency_key;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) NULL AFTER paid_at;

-- 회원 범위 UNIQUE. NULL은 서로 충돌하지 않으므로 기존 행(전부 NULL)과 키 없는 주문은 영향이 없다.
ALTER TABLE orders
    ADD UNIQUE KEY IF NOT EXISTS uk_orders_idempotency (member_id, idempotency_key);
