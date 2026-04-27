import { successResponse } from '@/lib/errors';
import pool from '@/lib/db';

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
      timestamp: Date.now(),
    });
  } finally {
    conn.release();
  }
}
