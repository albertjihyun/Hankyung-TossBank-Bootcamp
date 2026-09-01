-- ============================================================
-- 살래말래 (Sallae-Malrae) — DB 스키마
-- MariaDB 10.6+ / utf8mb4 / InnoDB
-- 노션 "최종 스키마 정의" 페이지의 §실제 SQL DDL을 그대로 옮긴 파일
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 1;

-- ------------------------------------------------------------
-- 1. users — 회원 정보
-- ------------------------------------------------------------
CREATE TABLE users (
  id            INT UNSIGNED       NOT NULL AUTO_INCREMENT,
  email         VARCHAR(255)       NOT NULL,
  password      VARCHAR(255)       NOT NULL                COMMENT 'bcrypt 해시',
  nickname      VARCHAR(20)        NOT NULL,
  refresh_token VARCHAR(255)       NULL                    COMMENT 'bcrypt 해시 저장 (ADR-005)',
  token_version INT UNSIGNED       NOT NULL DEFAULT 0      COMMENT '전체 세션 일괄 만료용',
  share_token   VARCHAR(32)        NULL                    COMMENT 'crypto.randomBytes(16).toString(hex)',
  level         TINYINT UNSIGNED   NOT NULL DEFAULT 1      COMMENT '1~5, LEVEL_THRESHOLDS 코드 상수와 매핑 (FK 없음, ADR-014)',
  created_at    DATETIME           NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email       (email),
  UNIQUE KEY uk_users_nickname    (nickname),
  UNIQUE KEY uk_users_share_token (share_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 2. categories — 카테고리 분류 (6개 시드 고정)
-- ------------------------------------------------------------
CREATE TABLE categories (
  id   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(20)  NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_categories_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO categories (id, name) VALUES
  (1, '패션/뷰티'),
  (2, '전자기기'),
  (3, '가전/가구'),
  (4, '음식/배달'),
  (5, '취미/여행'),
  (6, '기타');

-- ------------------------------------------------------------
-- 3. items — 쿨링오프 항목 (도메인 핵심)
-- ------------------------------------------------------------
CREATE TABLE items (
  id            INT UNSIGNED                          NOT NULL AUTO_INCREMENT,
  user_id       INT UNSIGNED                          NOT NULL,
  category_id   INT UNSIGNED                          NOT NULL,
  name          VARCHAR(100)                          NOT NULL,
  price         INT UNSIGNED                          NOT NULL,
  image         VARCHAR(512)                          NULL     COMMENT 'S3/GCS presigned URL 대응 여유',
  memo          VARCHAR(500)                          NULL,
  impulse_score TINYINT UNSIGNED                      NOT NULL COMMENT '1~10, 등록 시점 1회만 입력',
  status        ENUM('waiting','bought','passed')     NOT NULL DEFAULT 'waiting',
  expire_at     DATETIME                              NOT NULL COMMENT '정각 단위 저장',
  decided_at    DATETIME                              NULL,
  created_at    DATETIME                              NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_items_user_id     FOREIGN KEY (user_id)     REFERENCES users(id),
  CONSTRAINT fk_items_category_id FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT chk_items_status_decided CHECK (
    (status = 'waiting'             AND decided_at IS NULL    ) OR
    (status IN ('bought','passed')  AND decided_at IS NOT NULL)
  ),
  CONSTRAINT chk_items_price          CHECK (price > 0),
  CONSTRAINT chk_items_impulse_score  CHECK (impulse_score BETWEEN 1 AND 10),
  INDEX idx_items_user_status_expire (user_id, status, expire_at, id),
  INDEX idx_items_user_decided       (user_id, decided_at),
  INDEX idx_items_expire_status      (expire_at, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 4. email_logs — 이메일 발송 로그
-- ------------------------------------------------------------
CREATE TABLE email_logs (
  id           INT UNSIGNED                     NOT NULL AUTO_INCREMENT,
  item_id      INT UNSIGNED                     NOT NULL,
  type         ENUM('before_24h','expire')      NOT NULL,
  status       ENUM('success','fail')           NOT NULL,
  error_msg    VARCHAR(500)                     NULL,
  attempted_at DATETIME                         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_email_logs_item_id FOREIGN KEY (item_id) REFERENCES items(id) ON DELETE CASCADE,
  UNIQUE KEY uk_email_logs_item_type (item_id, type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ------------------------------------------------------------
-- 5. user_monthly_stats — 월별 집계 반정규화
-- ------------------------------------------------------------
CREATE TABLE user_monthly_stats (
  id           INT UNSIGNED        NOT NULL AUTO_INCREMENT,
  user_id      INT UNSIGNED        NOT NULL,
  year         SMALLINT UNSIGNED   NOT NULL,
  month        TINYINT UNSIGNED    NOT NULL,
  passed_count INT UNSIGNED        NOT NULL DEFAULT 0,
  bought_count INT UNSIGNED        NOT NULL DEFAULT 0,
  saved_amount BIGINT UNSIGNED     NOT NULL DEFAULT 0,
  spent_amount BIGINT UNSIGNED     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  CONSTRAINT fk_user_monthly_stats_user_id FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT chk_user_monthly_stats_month  CHECK (month BETWEEN 1 AND 12),
  UNIQUE KEY uk_user_monthly_stats (user_id, year, month)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
