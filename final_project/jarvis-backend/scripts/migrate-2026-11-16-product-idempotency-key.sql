-- 상품 등록 멱등 키 (02 D48) — I-10 `Idempotency-Key` 헤더(AI 서버의 draftId)를 저장해
-- 같은 등록 승인의 재전송을 한 상품으로 묶는다.
--
-- 적용 시점: **앱 배포 전.** 새 앱은 `product.idempotency_key` 컬럼이 없으면 `ddl-auto=validate`에 걸려
-- 기동을 거부한다. 컬럼은 NULL 허용이라 구 앱과 무해하게 공존한다(구 앱은 이 컬럼을 쓰지 않는다).
--
-- 재실행 무해: 컬럼·인덱스 모두 IF NOT EXISTS.
-- 되돌리기: ALTER TABLE product DROP INDEX uk_product_idempotency, DROP COLUMN idempotency_key;

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(64) NULL AFTER status;

-- 브랜드 범위 UNIQUE. NULL은 서로 충돌하지 않으므로 기존 행(전부 NULL)은 영향이 없다.
ALTER TABLE product
    ADD UNIQUE KEY IF NOT EXISTS uk_product_idempotency (brand_id, idempotency_key);
