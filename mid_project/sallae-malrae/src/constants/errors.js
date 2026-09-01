// 백엔드 에러 정의 — code(키) + message(개발자용 영문 디버깅) + HTTP status를 한 곳에서 관리.
// 사용자 노출 한국어 문구는 프론트 constants/errors.js의 ERROR_MESSAGES에서 code 기준으로 매핑.
// (개발 규칙 §4, API 관련 논의사항 §1·§2)

export const ERRORS = {
  // 인증
  UNAUTHORIZED:           { message: 'Authentication required.',                          status: 401 },
  LOGIN_FAILED:           { message: 'Invalid email or password.',                        status: 401 },
  INVALID_REFRESH_TOKEN:  { message: 'Refresh token missing or expired.',                 status: 401 },
  TOKEN_REUSE_DETECTED:   { message: 'Refresh token reuse detected — session revoked.',   status: 401 },

  // 입력 검증
  REQUIRED_FIELD:         { message: 'Required field is missing.',                        status: 400 },
  INVALID_EMAIL:          { message: 'Email format is invalid.',                          status: 400 },
  PASSWORD_TOO_SHORT:     { message: 'Password must be at least 8 characters.',           status: 400 },
  INVALID_PASSWORD:       { message: 'Password format is invalid.',                       status: 400 },
  INVALID_PRICE:          { message: 'Price must be a positive integer.',                 status: 400 },
  INVALID_EXPIRE_AT:      { message: 'expire_at is out of valid range.',                  status: 400 },
  INVALID_IMPULSE_SCORE:  { message: 'impulse_score must be between 1 and 10.',           status: 400 },
  INVALID_CATEGORY:       { message: 'category_id does not exist.',                       status: 400 },
  INVALID_IMAGE:          { message: 'Image file is invalid.',                            status: 400 },
  INVALID_STATUS:         { message: 'Invalid status transition.',                        status: 400 },

  // 중복
  DUPLICATE_EMAIL:        { message: 'Email already in use.',                             status: 409 },
  DUPLICATE_NICKNAME:     { message: 'Nickname already in use.',                          status: 409 },
  ALREADY_DECIDED:        { message: 'Item already decided.',                             status: 409 },

  // 권한
  NOT_OWNER:              { message: 'Access denied: not the owner.',                     status: 403 },

  // 리소스 없음
  ITEM_NOT_FOUND:         { message: 'Item not found.',                                   status: 404 },
  INVALID_SHARE_TOKEN:    { message: 'Share token is invalid or expired.',                status: 404 },

  // 정책 위반
  CANNOT_DELETE_DECIDED:  { message: 'Decided items cannot be deleted.',                  status: 400 },

  // 요청 제한
  RATE_LIMITED:           { message: 'Too many requests. Please try again later.',         status: 429 },

  // 서버 내부 오류
  SERVER_ERROR:           { message: 'An unexpected error occurred.',                      status: 500 },
};

// 사용자 노출용 한국어 메시지 매핑 — 백엔드가 내려준 code를 키로 조회.
// 매핑 없는 code는 호출 측에서 fallback 메시지 처리.
export const ERROR_MESSAGES = {
  LOGIN_FAILED:         '이메일 또는 비밀번호가 올바르지 않습니다.',
  REQUIRED_FIELD:       '필수 항목을 입력해주세요.',
  UNAUTHORIZED:         '로그인이 필요합니다.',
  INVALID_EMAIL:        '이메일 형식이 올바르지 않습니다.',
  PASSWORD_TOO_SHORT:   '비밀번호는 8자 이상이어야 합니다.',
  DUPLICATE_EMAIL:      '이미 사용 중인 이메일입니다.',
  DUPLICATE_NICKNAME:   '이미 사용 중인 닉네임입니다.',
  INVALID_PRICE:        '올바른 가격을 입력해주세요.',
  INVALID_CATEGORY:     '카테고리를 선택해주세요.',
  INVALID_IMPULSE_SCORE:'구매 충동 점수를 확인해주세요.',
  INVALID_EXPIRE_AT:    '쿨링오프 날짜는 오늘부터 30일 이내로 선택해주세요.',
  INVALID_IMAGE:        '이미지는 5MB 이하의 JPG, PNG, WebP, GIF만 가능해요.',
  RATE_LIMITED:         '요청이 너무 많아요. 잠시 후 다시 시도해주세요.',
};
