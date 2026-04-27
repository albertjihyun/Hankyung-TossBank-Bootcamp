import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { successResponse } from '@/lib/errors';

export async function GET(req: NextRequest) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const conn = await pool.getConnection();
  try {
    // 현재 참가 중인 방 확인
    const [memberRows] = await conn.query<any[]>(
      `SELECT rm.room_id, r.status, b.id AS battle_id
       FROM room_members rm
       JOIN rooms r ON r.id = rm.room_id
       LEFT JOIN battles b ON b.room_id = r.id
       WHERE rm.user_id = ? AND r.status != 'closed'
       LIMIT 1`,
      [auth.userId]
    );

    let activeRoom = null;
    if (memberRows.length > 0) {
      const row = memberRows[0];
      activeRoom = {
        roomId: row.room_id,
        battleId: row.battle_id ?? null,
        status: row.status === 'active'
          ? (row.battle_id ? 'active' : 'waiting')
          : row.status,
      };
    }

    return successResponse({
      user: { id: auth.userId, email: auth.email, nickname: auth.nickname },
      activeRoom,
    });
  } finally {
    conn.release();
  }
}
