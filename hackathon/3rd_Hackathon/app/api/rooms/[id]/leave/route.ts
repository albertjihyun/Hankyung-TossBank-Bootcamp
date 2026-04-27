import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { successResponse } from '@/lib/errors';

export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { id } = await params;
  const roomId = Number(id);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const [rooms] = await conn.query<any[]>(
      'SELECT host_id, status FROM rooms WHERE id = ?',
      [roomId]
    );

    if (rooms.length === 0) {
      await conn.commit();
      return successResponse({ success: true });
    }

    const room = rooms[0];

    if (room.host_id === auth.userId) {
      // Host leaves -> close room and remove all members.
      await conn.query("UPDATE rooms SET status = 'closed' WHERE id = ?", [roomId]);
      await conn.query('DELETE FROM room_members WHERE room_id = ?', [roomId]);
    } else {
      // Member leaves -> remove only this member.
      await conn.query('DELETE FROM room_members WHERE room_id = ? AND user_id = ?', [
        roomId,
        auth.userId,
      ]);

      // Safety: if host is missing in waiting room, close it.
      const [hostMember] = await conn.query<any[]>(
        'SELECT id FROM room_members WHERE room_id = ? AND user_id = ? LIMIT 1',
        [roomId, room.host_id]
      );
      if (hostMember.length === 0) {
        await conn.query("UPDATE rooms SET status = 'closed' WHERE id = ?", [roomId]);
      }
    }

    await conn.commit();
    return successResponse({ success: true });
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}
