// 로컬 디버깅용 — 가상의 정각 시각을 인자로 받아 발송 로직 검증
// 사용법: node --env-file=.env.local scripts/dev-notify.js "2026-05-17T03:00:00"
// DRY_RUN 모드: DRY_RUN=1 node --env-file=.env.local scripts/dev-notify.js "2026-05-17T03:00:00"

import { sendDueNotifications } from '../src/lib/notifier.js';
import { pool } from '../src/lib/db.js';

const arg = process.argv[2];
const now = arg ? new Date(arg) : new Date();

if (isNaN(now.getTime())) {
  console.error(`[dev-notify] 유효하지 않은 시각: ${arg}`);
  process.exit(1);
}

console.log(`[dev-notify] 기준 시각: ${now.toISOString()} (DRY_RUN=${process.env.DRY_RUN ?? '0'})`);

try {
  const result = await sendDueNotifications(now);
  console.log(`[dev-notify] 완료 — before_24h: ${result.before24h}, expire: ${result.expire}`);
} catch (err) {
  console.error('[dev-notify] 오류:', err);
  await pool.end().catch(() => {});
  process.exit(1);
}

await pool.end().catch(() => {});
process.exit(0);
