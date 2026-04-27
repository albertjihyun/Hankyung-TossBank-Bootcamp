import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { errorResponse, successResponse } from '@/lib/errors';

export async function GET(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { id } = await params;
  const battleId = Number(id);

  const conn = await pool.getConnection();
  try {
    const [rows] = await conn.query<any[]>(
      `SELECT
         u.id AS userId,
         u.nickname,
         p.cash_balance + COALESCE(SUM(h.quantity * latest.price), 0) AS finalAssets,
         b.initial_cash AS initialCash
       FROM portfolios p
       JOIN users u ON p.user_id = u.id
       JOIN battles b ON b.id = p.battle_id
       LEFT JOIN holdings h ON h.portfolio_id = p.id
       LEFT JOIN (
         SELECT sp1.stock_id, sp1.price
         FROM stock_prices sp1
         WHERE sp1.battle_id = ?
           AND sp1.recorded_at = (
             SELECT MAX(sp2.recorded_at)
             FROM stock_prices sp2
             WHERE sp2.stock_id = sp1.stock_id AND sp2.battle_id = ?
           )
       ) latest ON latest.stock_id = h.stock_id
       WHERE p.battle_id = ?
       GROUP BY p.id
       ORDER BY finalAssets DESC`,
      [battleId, battleId, battleId]
    );

    if (rows.length === 0) return errorResponse('BATTLE_001', 404);

    const isMember = rows.some((r) => r.userId === auth.userId);
    if (!isMember) return errorResponse('AUTH_003', 403);

    const rankings = rows.map((r, i) => ({
      rank: i + 1,
      userId: r.userId,
      nickname: r.nickname,
      finalAssets: Number(r.finalAssets),
      changeRate: (Number(r.finalAssets) - r.initialCash) / r.initialCash,
      isMe: r.userId === auth.userId,
    }));

    return successResponse({ rankings });
  } finally {
    conn.release();
  }
}
