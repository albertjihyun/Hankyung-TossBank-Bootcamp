import { query, getConnection } from '@/lib/db';
import { getSessionUser } from '@/lib/auth';
import { successResponse, errorResponse } from '@/lib/response';
import { calculateLevel } from '@/lib/level';
import { ITEM_STATUS } from '@/constants/status';

// PATCH /api/items/:id/status — 구매함/아낌 결정.
// ADR-003: items + user_monthly_stats + users.level 트랜잭션 처리.
// ADR-007: 같은 상태 재요청 시 멱등 처리 (200, 변경 없음).
export async function PATCH(request, { params }) {
  const user = getSessionUser(request);
  if (!user) return errorResponse('UNAUTHORIZED');

  const id = Number((await params).id);

  let body;
  try {
    body = await request.json();
  } catch {
    return errorResponse('REQUIRED_FIELD');
  }

  const { status } = body ?? {};
  if (status !== 'BOUGHT' && status !== 'PASSED') return errorResponse('INVALID_STATUS');
  const dbStatus = status.toLowerCase();

  const conn = await getConnection();
  try {
    await conn.beginTransaction();

    const rows = await conn.query(
      'SELECT id, user_id, status, price, decided_at FROM items WHERE id = ? FOR UPDATE',
      [id],
    );

    if (rows.length === 0) {
      await conn.rollback();
      return errorResponse('ITEM_NOT_FOUND');
    }

    const item = rows[0];

    if (item.user_id !== user.user_id) {
      await conn.rollback();
      return errorResponse('NOT_OWNER');
    }

    // 멱등성 — 이미 같은 상태면 현재 stats 그대로 반환 (ADR-007)
    if (item.status === dbStatus) {
      await conn.rollback();
      const [statsRow] = await query(
        `SELECT COALESCE(SUM(passed_count), 0) AS passed_count,
                COALESCE(SUM(saved_amount), 0) AS saved_amount
         FROM user_monthly_stats WHERE user_id = ?`,
        [user.user_id],
      );
      const [userRow] = await query('SELECT level FROM users WHERE id = ?', [user.user_id]);
      return successResponse(
        {
          item: { item_id: id, status: item.status, decided_at: item.decided_at, days_left: 0 },
          updatedStats: {
            passed_count: Number(statsRow.passed_count),
            saved_amount: Number(statsRow.saved_amount),
            level: userRow.level,
            levelUp: false,
          },
        },
        'Decision saved.',
      );
    }

    // 이미 다른 상태로 결정된 경우 (ALREADY_DECIDED)
    if (item.status !== ITEM_STATUS.WAITING) {
      await conn.rollback();
      return errorResponse('ALREADY_DECIDED');
    }

    // 1. items 상태 변경
    await conn.query(
      'UPDATE items SET status = ?, decided_at = NOW() WHERE id = ?',
      [dbStatus, id],
    );

    const [updated] = await conn.query('SELECT decided_at FROM items WHERE id = ?', [id]);
    const decidedAt = updated.decided_at;

    // 2. user_monthly_stats UPSERT (decided_at 기준 월, ADR-002)
    const passedInc = dbStatus === ITEM_STATUS.PASSED ? 1 : 0;
    const boughtInc = dbStatus === ITEM_STATUS.BOUGHT ? 1 : 0;
    const savedInc  = dbStatus === ITEM_STATUS.PASSED ? Number(item.price) : 0;
    const spentInc  = dbStatus === ITEM_STATUS.BOUGHT ? Number(item.price) : 0;

    await conn.query(
      `INSERT INTO user_monthly_stats
         (user_id, year, month, passed_count, bought_count, saved_amount, spent_amount)
       VALUES (?, YEAR(?), MONTH(?), ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE
         passed_count = passed_count + ?,
         bought_count = bought_count + ?,
         saved_amount = saved_amount + ?,
         spent_amount = spent_amount + ?`,
      [user.user_id, decidedAt, decidedAt, passedInc, boughtInc, savedInc, spentInc,
       passedInc, boughtInc, savedInc, spentInc],
    );

    // 3. 전체 누적 통계로 레벨 재계산 (ADR-014)
    const [statsRow] = await conn.query(
      `SELECT COALESCE(SUM(passed_count), 0) AS passed_count,
              COALESCE(SUM(saved_amount), 0) AS saved_amount
       FROM user_monthly_stats WHERE user_id = ?`,
      [user.user_id],
    );

    const totalPassed = Number(statsRow.passed_count);
    const totalSaved  = Number(statsRow.saved_amount);
    const newLevel    = calculateLevel(totalPassed, totalSaved);

    const [userRow] = await conn.query('SELECT level FROM users WHERE id = ?', [user.user_id]);
    const oldLevel = userRow.level;

    await conn.query('UPDATE users SET level = ? WHERE id = ?', [newLevel, user.user_id]);

    await conn.commit();

    return successResponse(
      {
        item: { item_id: id, status: dbStatus, decided_at: decidedAt, days_left: 0 },
        updatedStats: {
          passed_count: totalPassed,
          saved_amount: totalSaved,
          level: newLevel,
          levelUp: newLevel > oldLevel,
        },
      },
      'Decision saved.',
    );
  } catch (err) {
    await conn.rollback();
    throw err;
  } finally {
    conn.release();
  }
}
