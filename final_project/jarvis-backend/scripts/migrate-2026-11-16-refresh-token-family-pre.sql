-- 리프레시 토큰 패밀리 [앱 배포 전] (02 D50) — 회전된 RT를 지우지 않고 남겨 재사용(탈취)을 탐지한다.
--
-- 적용 시점: **앱 배포 전.** 새 앱은 `family_id`·`rotated_at` 컬럼이 없으면 `ddl-auto=validate`에 걸려
-- 기동을 거부한다. 이 단계의 `family_id`는 NULL 허용이다 — 배포 창 동안 구 앱이 넣는 행은 이 값을 모른다.
-- NOT NULL 전환은 배포 후 `-post` 스크립트가 한다(expand/contract).
--
-- 재실행 무해: 컬럼·인덱스 IF NOT EXISTS, 백필은 NULL 행만 대상.
-- 되돌리기: ALTER TABLE refresh_token DROP INDEX idx_refresh_token_family, DROP INDEX idx_refresh_token_expires,
--           DROP COLUMN rotated_at, DROP COLUMN family_id;

ALTER TABLE refresh_token
    ADD COLUMN IF NOT EXISTS family_id  CHAR(36) NULL AFTER token_hash,
    ADD COLUMN IF NOT EXISTS rotated_at DATETIME NULL AFTER family_id;

-- 기존 RT는 각자 하나의 패밀리로 본다 — 어느 로그인에서 이어졌는지는 복원할 수 없다
UPDATE refresh_token SET family_id = UUID() WHERE family_id IS NULL;

ALTER TABLE refresh_token
    ADD KEY IF NOT EXISTS idx_refresh_token_family (family_id),
    ADD KEY IF NOT EXISTS idx_refresh_token_expires (expires_at);
