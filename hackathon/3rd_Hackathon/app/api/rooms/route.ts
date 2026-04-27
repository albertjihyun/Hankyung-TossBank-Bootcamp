import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { errorResponse, successResponse } from '@/lib/errors';

export async function GET() {
  const conn = await pool.getConnection();
  try {
    // Cleanup invalid waiting rooms before returning lobby list.
    await conn.query(
      `UPDATE rooms r
       LEFT JOIN room_members host_rm
         ON host_rm.room_id = r.id AND host_rm.user_id = r.host_id
       SET r.status = 'closed'
       WHERE r.status = 'waiting'
         AND host_rm.id IS NULL`
    );

    const [rows] = await conn.query<any[]>(
      `SELECT r.id, r.title, u.nickname AS hostNickname,
              COUNT(rm.id) AS memberCount, r.status
       FROM rooms r
       JOIN users u ON u.id = r.host_id
       LEFT JOIN room_members rm ON rm.room_id = r.id
       WHERE r.status = 'waiting'
         AND EXISTS (
           SELECT 1
           FROM room_members host_rm
           WHERE host_rm.room_id = r.id
             AND host_rm.user_id = r.host_id
         )
       GROUP BY r.id
       ORDER BY r.created_at DESC`
    );
    return successResponse({
      rooms: rows.map((r) => ({ ...r, maxMembers: 4, memberCount: Number(r.memberCount) })),
    });
  } finally {
    conn.release();
  }
}

export async function POST(req: NextRequest) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { title } = await req.json();
  if (!title?.trim()) {
    return Response.json({ success: false, error: { code: 'INVALID', message: '방 이름을 입력해주세요' } }, { status: 400 });
  }

  const conn = await pool.getConnection();
  try {
    // 이미 참가 중인 방 확인
    const [existing] = await conn.query<any[]>(
      `SELECT rm.room_id FROM room_members rm
       JOIN rooms r ON r.id = rm.room_id
       WHERE rm.user_id = ? AND r.status != 'closed'
       LIMIT 1`,
      [auth.userId]
    );
    if (existing.length > 0) return errorResponse('ROOM_003');

    await conn.beginTransaction();
    const [result] = await conn.query<any>(
      'INSERT INTO rooms (title, host_id) VALUES (?, ?)',
      [title.trim(), auth.userId]
    );
    const roomId = result.insertId;
    await conn.query(
      'INSERT INTO room_members (room_id, user_id) VALUES (?, ?)',
      [roomId, auth.userId]
    );
    await conn.commit();

    return successResponse({ roomId }, 201);
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}
