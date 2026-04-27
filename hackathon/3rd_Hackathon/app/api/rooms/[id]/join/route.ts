import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { errorResponse, successResponse } from '@/lib/errors';

export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { id } = await params;
  const roomId = Number(id);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // 이미 참가 중인 다른 방 확인
    const [existing] = await conn.query<any[]>(
      `SELECT rm.room_id FROM room_members rm
       JOIN rooms r ON r.id = rm.room_id
       WHERE rm.user_id = ? AND r.status != 'closed'
       LIMIT 1`,
      [auth.userId]
    );
    if (existing.length > 0) {
      await conn.rollback();
      return errorResponse('ROOM_003');
    }

    const [rooms] = await conn.query<any[]>(
      'SELECT id, status FROM rooms WHERE id = ? FOR UPDATE',
      [roomId]
    );
    if (rooms.length === 0) {
      await conn.rollback();
      return errorResponse('ROOM_001', 404);
    }

    const room = rooms[0];
    if (room.status !== 'waiting') {
      await conn.rollback();
      return errorResponse('ROOM_004');
    }

    const [countRows] = await conn.query<any[]>(
      'SELECT COUNT(*) AS cnt FROM room_members WHERE room_id = ?',
      [roomId]
    );
    if (countRows[0].cnt >= 4) {
      await conn.rollback();
      return errorResponse('ROOM_002');
    }

    await conn.query(
      'INSERT INTO room_members (room_id, user_id) VALUES (?, ?)',
      [roomId, auth.userId]
    );

    // 4명 도달 시 배틀 자동 시작
    const newCount = Number(countRows[0].cnt) + 1;
    if (newCount >= 4) {
      const [battleResult] = await conn.query<any>(
        `INSERT INTO battles (room_id, ends_at)
         VALUES (?, DATE_ADD(NOW(), INTERVAL 3 MINUTE))`,
        [roomId]
      );
      const battleId = battleResult.insertId;

      await conn.query("UPDATE rooms SET status = 'active' WHERE id = ?", [roomId]);

      const [members] = await conn.query<any[]>(
        'SELECT user_id FROM room_members WHERE room_id = ?',
        [roomId]
      );
      for (const m of members) {
        await conn.query(
          'INSERT INTO portfolios (battle_id, user_id, cash_balance) VALUES (?, ?, 1000000)',
          [battleId, m.user_id]
        );
      }

      const [stocks] = await conn.query<any[]>('SELECT id, initial_price FROM stocks');
      for (const s of stocks) {
        await conn.query(
          'INSERT INTO stock_prices (battle_id, stock_id, price) VALUES (?, ?, ?)',
          [battleId, s.id, s.initial_price]
        );
      }

      await conn.commit();
      return successResponse({ roomId, battleId });
    }

    await conn.commit();
    return successResponse({ roomId });
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}
