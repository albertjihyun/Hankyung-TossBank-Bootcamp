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

    const [rooms] = await conn.query<any[]>(
      'SELECT host_id, status FROM rooms WHERE id = ? FOR UPDATE',
      [roomId]
    );
    if (rooms.length === 0) {
      await conn.rollback();
      return errorResponse('ROOM_001', 404);
    }

    const room = rooms[0];
    if (room.host_id !== auth.userId) {
      await conn.rollback();
      return errorResponse('AUTH_003', 403);
    }

    if (room.status !== 'waiting') {
      await conn.rollback();
      return errorResponse('ROOM_004');
    }

    // 이미 배틀 있는지 확인
    const [battles] = await conn.query<any[]>(
      'SELECT id FROM battles WHERE room_id = ?',
      [roomId]
    );
    if (battles.length > 0) {
      await conn.rollback();
      return errorResponse('BATTLE_EXISTS');
    }

    const [countRows] = await conn.query<any[]>(
      'SELECT COUNT(*) AS cnt FROM room_members WHERE room_id = ?',
      [roomId]
    );
    if (countRows[0].cnt < 2) {
      await conn.rollback();
      return errorResponse('ROOM_005');
    }

    // 배틀 생성 (ends_at = now + 3분)
    const [battleResult] = await conn.query<any>(
      `INSERT INTO battles (room_id, ends_at)
       VALUES (?, DATE_ADD(NOW(), INTERVAL 3 MINUTE))`,
      [roomId]
    );
    const battleId = battleResult.insertId;

    await conn.query("UPDATE rooms SET status = 'active' WHERE id = ?", [roomId]);

    // 참가자별 포트폴리오 생성
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

    // 초기 주가 삽입
    const [stocks] = await conn.query<any[]>('SELECT id, initial_price FROM stocks');
    for (const s of stocks) {
      await conn.query(
        'INSERT INTO stock_prices (battle_id, stock_id, price) VALUES (?, ?, ?)',
        [battleId, s.id, s.initial_price]
      );
    }

    await conn.commit();
    return successResponse({ battleId });
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}
