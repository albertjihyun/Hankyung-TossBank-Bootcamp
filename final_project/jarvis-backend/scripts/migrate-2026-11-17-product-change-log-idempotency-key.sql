-- 재고 수정 멱등 키 (02 D52) — I-11 `Idempotency-Key`(AI 서버의 draftId)를 재고 변경 로그에 남겨,
-- 같은 수정 승인의 재전송이 그 사이 팔린 수량을 되살리지 못하게 한다.
--
-- 적용 시점: **앱 배포 전.** 새 앱은 `product_change_logs.idempotency_key` 컬럼이 없으면
-- `ddl-auto=validate`에 걸려 기동을 거부한다. NULL 허용이라 구 앱과 무해하게 공존한다.
--
-- 재실행 무해: 컬럼·인덱스 모두 IF NOT EXISTS.
-- 되돌리기: ALTER TABLE product_change_logs DROP INDEX idx_pclog_idempotency, DROP COLUMN idempotency_key;

ALTER TABLE product_change_logs
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) NULL AFTER new_value;

ALTER TABLE product_change_logs
    ADD KEY IF NOT EXISTS idx_pclog_idempotency (product_id, idempotency_key);
