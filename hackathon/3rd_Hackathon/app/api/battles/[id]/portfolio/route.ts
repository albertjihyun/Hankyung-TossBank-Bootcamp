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
    const [portfolios] = await conn.query<any[]>(
      'SELECT id, cash_balance FROM portfolios WHERE battle_id = ? AND user_id = ?',
      [battleId, auth.userId]
    );

    if (portfolios.length === 0) return errorResponse('AUTH_003', 403);

    const portfolioId: number = portfolios[0].id;
    const cashBalance: number = portfolios[0].cash_balance;

    const [holdings] = await conn.query<any[]>(
      `SELECT h.stock_id AS stockId, s.symbol, h.quantity, h.avg_buy_price AS avgBuyPrice
       FROM holdings h JOIN stocks s ON s.id = h.stock_id
       WHERE h.portfolio_id = ?`,
      [portfolioId]
    );

    return successResponse({ cashBalance, holdings });
  } finally {
    conn.release();
  }
}
