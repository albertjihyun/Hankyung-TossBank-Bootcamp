import { sendDueNotifications } from '../src/lib/notifier.js';
import { pool } from '../src/lib/db.js';

try {
  const result = await sendDueNotifications(new Date());
  console.log(`[notifier] 완료 — before_24h: ${result.before24h}, expire: ${result.expire}`);
} catch (err) {
  console.error('[notifier] 오류:', err);
  await pool.end().catch(() => {});
  process.exit(1);
}

await pool.end().catch(() => {});
process.exit(0);
