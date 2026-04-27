import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { errorResponse, successResponse } from '@/lib/errors';

export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { id } = await params;
  const battleId = Number(id);
  const { stockId, type, quantity } = await req.json();

  if (!stockId || !type || !quantity || quantity <= 0) {
    return Response.json({ success: false, error: { code: 'INVALID', message: '잘못된 요청이에요' } }, { status: 400 });
  }

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // 배틀 상태 확인
    const [battles] = await conn.query<any[]>(
      'SELECT status, ends_at FROM battles WHERE id = ?',
      [battleId]
    );
    if (battles.length === 0) {
      await conn.rollback();
      return errorResponse('BATTLE_001', 404);
    }
    if (battles[0].status === 'ended' || new Date(battles[0].ends_at) <= new Date()) {
      await conn.rollback();
      return errorResponse('BATTLE_002');
    }

    // 현재가 조회
    const [priceRows] = await conn.query<any[]>(
      `SELECT price FROM stock_prices
       WHERE battle_id = ? AND stock_id = ?
       ORDER BY recorded_at DESC LIMIT 1`,
      [battleId, stockId]
    );
    if (priceRows.length === 0) {
      await conn.rollback();
      return errorResponse('BATTLE_001', 404);
    }
    const price = priceRows[0].price;

    // 포트폴리오 조회 (FOR UPDATE)
    const [portfolios] = await conn.query<any[]>(
      'SELECT id, cash_balance FROM portfolios WHERE battle_id = ? AND user_id = ? FOR UPDATE',
      [battleId, auth.userId]
    );
    if (portfolios.length === 0) {
      await conn.rollback();
      return errorResponse('AUTH_003', 403);
    }
    const portfolio = portfolios[0];

    if (type === 'buy') {
      const cost = price * quantity;
      if (portfolio.cash_balance < cost) {
        await conn.rollback();
        return errorResponse('BATTLE_003');
      }

      await conn.query(
        'UPDATE portfolios SET cash_balance = cash_balance - ? WHERE id = ?',
        [cost, portfolio.id]
      );
      await conn.query(
        `INSERT INTO holdings (portfolio_id, stock_id, quantity, avg_buy_price)
         VALUES (?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE
           avg_buy_price = (avg_buy_price * quantity + ? * ?) / (quantity + ?),
           quantity = quantity + ?`,
        [portfolio.id, stockId, quantity, price, price, quantity, quantity, quantity]
      );
    } else {
      // sell
      const [holdings] = await conn.query<any[]>(
        'SELECT id, quantity FROM holdings WHERE portfolio_id = ? AND stock_id = ? FOR UPDATE',
        [portfolio.id, stockId]
      );
      if (holdings.length === 0 || holdings[0].quantity < quantity) {
        await conn.rollback();
        return errorResponse('BATTLE_004');
      }

      const gain = price * quantity;
      await conn.query(
        'UPDATE portfolios SET cash_balance = cash_balance + ? WHERE id = ?',
        [gain, portfolio.id]
      );

      const newQty = holdings[0].quantity - quantity;
      if (newQty === 0) {
        await conn.query('DELETE FROM holdings WHERE id = ?', [holdings[0].id]);
      } else {
        await conn.query('UPDATE holdings SET quantity = ? WHERE id = ?', [newQty, holdings[0].id]);
      }
    }

    await conn.query(
      'INSERT INTO transactions (portfolio_id, stock_id, type, quantity, price) VALUES (?, ?, ?, ?, ?)',
      [portfolio.id, stockId, type, quantity, price]
    );

    await conn.commit();

    // 최신 상태 반환
    const [updatedPortfolio] = await conn.query<any[]>(
      'SELECT cash_balance FROM portfolios WHERE id = ?',
      [portfolio.id]
    );
    const [updatedHolding] = await conn.query<any[]>(
      'SELECT quantity, avg_buy_price AS avgBuyPrice FROM holdings WHERE portfolio_id = ? AND stock_id = ?',
      [portfolio.id, stockId]
    );

    return successResponse({
      cashBalance: updatedPortfolio[0].cash_balance,
      holding: updatedHolding.length > 0
        ? { stockId, quantity: updatedHolding[0].quantity, avgBuyPrice: updatedHolding[0].avgBuyPrice }
        : null,
    });
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}
