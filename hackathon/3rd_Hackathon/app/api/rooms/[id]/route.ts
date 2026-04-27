import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { errorResponse, successResponse } from '@/lib/errors';

export async function GET(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { id } = await params;
  const roomId = Number(id);

  const conn = await pool.getConnection();
  try {
    const [rooms] = await conn.query<any[]>(
      'SELECT id, title, host_id, status FROM rooms WHERE id = ?',
      [roomId]
    );
    if (rooms.length === 0) return errorResponse('ROOM_001', 404);

    const room = rooms[0];

    const [members] = await conn.query<any[]>(
      `SELECT u.id AS userId, u.nickname, (u.id = ?) AS isHost
       FROM room_members rm JOIN users u ON u.id = rm.user_id
       WHERE rm.room_id = ?`,
      [room.host_id, roomId]
    );

    const [battles] = await conn.query<any[]>(
      'SELECT id FROM battles WHERE room_id = ? LIMIT 1',
      [roomId]
    );

    const battleId = battles.length > 0 ? battles[0].id : null;

    return successResponse({
      id: room.id,
      title: room.title,
      hostId: room.host_id,
      status: room.status,
      members: members.map((m) => ({ ...m, isHost: Boolean(m.isHost) })),
      battleStarted: battleId !== null,
      battleId,
    });
  } finally {
    conn.release();
  }
}
