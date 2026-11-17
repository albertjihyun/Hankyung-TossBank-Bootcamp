-- 회원 탈퇴 (02 D55) — 탈퇴 시각. 값이 있는 행은 개인정보가 지워진 행이다.
-- NULL 허용 컬럼 추가라 구 앱과 공존한다.
ALTER TABLE member ADD COLUMN withdrawn_at DATETIME NULL AFTER agreed_privacy_at;
