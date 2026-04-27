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
    const [battles] = await conn.query<any[]>(
      'SELECT id, status, ends_at, (NOW() >= ends_at) AS is_ended FROM battles WHERE id = ?',
      [battleId]
    );
    if (battles.length === 0) return errorResponse('BATTLE_001', 404);

    const battle = battles[0];

    // DB 레벨에서 비교해 타임존 불일치 방지
    const battleEnded = Boolean(battle.is_ended) || battle.status === 'ended';

    if (battleEnded && battle.status === 'active') {
      await conn.query(
        "UPDATE battles SET status = 'ended' WHERE id = ?",
        [battleId]
      );
      await conn.query(
        "UPDATE rooms SET status = 'closed' WHERE id = (SELECT room_id FROM battles WHERE id = ?)",
        [battleId]
      );
    }

    // 10초마다 가격 생성 (request-driven, race condition 방지: DB 레벨 조건)
    if (!battleEnded) {
      await conn.query(
        `INSERT INTO stock_prices (battle_id, stock_id, price)
         SELECT sp_ref.battle_id, sp_ref.stock_id,
           GREATEST(1, ROUND(sp_ref.price * (1 + (RAND() - 0.5) * 2 * st.volatility)))
         FROM (
           SELECT sp1.battle_id, sp1.stock_id, sp1.price
           FROM stock_prices sp1
           WHERE sp1.battle_id = ?
             AND sp1.recorded_at = (
               SELECT MAX(sp2.recorded_at) FROM stock_prices sp2
               WHERE sp2.battle_id = sp1.battle_id AND sp2.stock_id = sp1.stock_id
             )
         ) sp_ref
         JOIN stocks st ON st.id = sp_ref.stock_id
         WHERE NOT EXISTS (
           SELECT 1 FROM stock_prices sp3
           WHERE sp3.battle_id = ? AND sp3.stock_id = sp_ref.stock_id
             AND sp3.recorded_at > DATE_SUB(NOW(), INTERVAL 10 SECOND)
         )`,
        [battleId, battleId]
      );
    }

    const endsAt = battle.ends_at instanceof Date
    ? battle.ends_at.toISOString()
    : String(battle.ends_at).replace(' ', 'T') + 'Z';
  return await buildPricesResponse(conn, battleId, battleEnded, endsAt);
  } finally {
    conn.release();
  }
}

async function buildPricesResponse(conn: any, battleId: number, battleEnded: boolean, endsAt: any) {
  const [stocks]: any = await conn.query(
    'SELECT id, symbol, initial_price FROM stocks'
  );

  const prices = await Promise.all(
    stocks.map(async (stock: any) => {
      const [history]: any = await conn.query(
        `SELECT price, recorded_at AS recordedAt
         FROM stock_prices
         WHERE battle_id = ? AND stock_id = ?
         ORDER BY recorded_at DESC LIMIT 5`,
        [battleId, stock.id]
      );
      const reversed = [...history].reverse();
      const currentPrice = reversed[reversed.length - 1]?.price ?? stock.initial_price;
      const changeRate = (currentPrice - stock.initial_price) / stock.initial_price;
      return {
        stockId: stock.id,
        symbol: stock.symbol,
        currentPrice,
        initialPrice: stock.initial_price,
        changeRate,
        history: reversed,
      };
    })
  );

  return successResponse({ prices, battleEnded, endsAt });
}
