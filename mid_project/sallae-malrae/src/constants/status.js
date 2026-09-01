// items.status DB 값(영문 ENUM)을 상수로 관리.
// UI 한국어 매핑(대기중/구매함/아낌)은 프론트 constants/status-labels.js에서 별도 — 관심사 분리.
// (개발 규칙 §4, 변경 이력 2026-05-12 오전)

export const ITEM_STATUS = {
  WAITING: 'waiting',
  BOUGHT:  'bought',
  PASSED:  'passed',
};
