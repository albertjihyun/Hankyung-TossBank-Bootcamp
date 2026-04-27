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
      'SELECT id, status, ends_at, initial_cash FROM battles WHERE id = ?',
      [battleId]
    );
    if (battles.length === 0) return errorResponse('BATTLE_001', 404);

    // 참가자 확인 (portfolios 기준: room_members는 배틀 후 삭제될 수 있음)
    const [portfolios] = await conn.query<any[]>(
      'SELECT id FROM portfolios WHERE battle_id = ? AND user_id = ?',
      [battleId, auth.userId]
    );
    if (portfolios.length === 0) return errorResponse('AUTH_003', 403);

    const [members] = await conn.query<any[]>(
      `SELECT u.id AS userId, u.nickname
       FROM portfolios p
       JOIN users u ON u.id = p.user_id
       WHERE p.battle_id = ?`,
      [battleId]
    );

    const b = battles[0];
    const endsAt = b.ends_at instanceof Date
      ? b.ends_at.toISOString()
      : String(b.ends_at).replace(' ', 'T') + 'Z';
    return successResponse({
      id: b.id,
      status: b.status,
      endsAt,
      initialCash: b.initial_cash,
      participants: members,
    });
  } finally {
    conn.release();
  }
}
