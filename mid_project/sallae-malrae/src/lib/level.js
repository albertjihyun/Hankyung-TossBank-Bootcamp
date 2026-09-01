// 짠돌이 레벨 메타데이터 + 산정 함수. ADR-014에 따라 DB 테이블 없이 코드 상수가 단일 진실 원천.
// users.level (TINYINT UNSIGNED) 컬럼은 이 상수의 level 값과 매핑 (FK 없음).
// 결정 트랜잭션(PATCH /items/:id/status, ADR-003)에서 calculateLevel 호출 후 users.level 갱신.

// 키 이름(threshold_count, threshold_amount)은 ADR-014 본문과 동일하게 snake_case 유지 —
// DB 도메인 어휘(passed_count, saved_amount)와 짝지어 읽기 위함.
// 이미지 경로는 public/images/levels/ 실제 폴더와 일치 (노션 ADR-014의 '/levels/'는 갱신 대상).
export const LEVEL_THRESHOLDS = [
  {
    level: 1,
    name: "충동 새싹",
    description: "절약의 첫 걸음을\n막 떼셨어요!",
    threshold_count: 0,
    threshold_amount: 0,
    image: "/images/levels/level-1.png",
  },
  {
    level: 2,
    name: "절약 입문자",
    description: "조금씩 절약의 감을\n잡아가고 있어요!",
    threshold_count: 5,
    threshold_amount: 25_000,
    image: "/images/levels/level-2.png",
  },
  {
    level: 3,
    name: "참을 인 달인",
    description: "참는 힘이\n점점 강해지고 있어요!",
    threshold_count: 15,
    threshold_amount: 75_000,
    image: "/images/levels/level-3.png",
  },
  {
    level: 4,
    name: "짠돌이",
    description: "충동구매를 잘 막아내는\n절약 고수예요!",
    threshold_count: 30,
    threshold_amount: 150_000,
    image: "/images/levels/level-4.png",
  },
  {
    level: 5,
    name: "전설의 짠돌이",
    description: "충동구매를 꾸준히 참아낸\n절약의 달인이에요!",
    threshold_count: 50,
    threshold_amount: 250_000,
    image: "/images/levels/level-5.png",
  },
];

export function getLevelMeta(level) {
  return (
    LEVEL_THRESHOLDS.find((tier) => tier.level === level) ?? LEVEL_THRESHOLDS[0]
  );
}

// 누적 참은 횟수와 절약 금액으로 현재 레벨(1~5) 계산.
// 두 임계값을 모두 만족(AND)하는 가장 높은 레벨 반환 —
// 한 쪽만 만족하면 "운 좋게 비싼 거 몇 개 참아 상위 레벨" 같은 부자연스러움이 생기지 않게 함.
// LEVEL_THRESHOLDS가 level 오름차순이라 마지막 만족 tier가 최종 레벨.
export function calculateLevel(passedCount, savedAmount) {
  let result = 1;
  for (const tier of LEVEL_THRESHOLDS) {
    if (
      passedCount >= tier.threshold_count &&
      savedAmount >= tier.threshold_amount
    ) {
      result = tier.level;
    }
  }
  return result;
}
