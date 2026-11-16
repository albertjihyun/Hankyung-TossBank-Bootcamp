-- 리프레시 토큰 패밀리 [앱 배포 후] (02 D50) — `family_id`를 NOT NULL로 조인다.
--
-- 적용 시점: **앱 배포 후.** 새 앱이 모든 RT에 `family_id`를 채우기 시작한 뒤여야 한다. 먼저 조이면
-- 배포 창 동안 구 앱의 로그인·재발급이 INSERT 실패로 죽는다.
--
-- 재실행 무해: 백필은 NULL 행만, MODIFY는 같은 정의로 수렴.

-- 배포 창 동안 구 앱이 넣은 행 — 각자 하나의 패밀리로 채운다
UPDATE refresh_token SET family_id = UUID() WHERE family_id IS NULL;

ALTER TABLE refresh_token
    MODIFY COLUMN family_id CHAR(36) NOT NULL;
