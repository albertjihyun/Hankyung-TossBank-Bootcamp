// 11개 API가 import할 공통 검증 유틸.
// 도메인 특화 검증(impulse_score 범위, expire_at 정각 단위 등)은 각 API 작업 시점에 추가.

// 이메일 형식 — 간단한 RFC 5322 부분집합. INVALID_EMAIL.
export function isValidEmail(email) {
  return typeof email === 'string' && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

// 비밀번호 최소 길이 — 8자 이상. PASSWORD_TOO_SHORT.
export function isValidPasswordLength(password) {
  return typeof password === 'string' && password.length >= 8 && password.length <= 100;
}

// 닉네임 길이 — 1~20자 (users.nickname VARCHAR(20) NOT NULL).
export function isValidNicknameLength(nickname) {
  return typeof nickname === 'string' && nickname.length >= 1 && nickname.length <= 20;
}

// 빈 문자열·null·undefined를 모두 invalid로 처리. REQUIRED_FIELD.
export function isNonEmptyString(v) {
  return typeof v === 'string' && v.length > 0;
}

// 양의 정수 — price, page, limit 등 양수 INT 검증에 재사용.
export function isPositiveInteger(v) {
  return typeof v === 'number' && Number.isInteger(v) && v > 0;
}
