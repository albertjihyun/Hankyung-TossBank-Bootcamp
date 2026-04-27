import { NextRequest } from 'next/server';
import pool from '@/lib/db';
import { authenticate, isResponse } from '@/lib/apiMiddleware';
import { errorResponse, successResponse } from '@/lib/errors';

export async function POST(req: NextRequest, { params }: { params: Promise<{ id: string }> }) {
  const auth = await authenticate(req);
  if (isResponse(auth)) return auth;

  const { id } = await params;
  const battleId = Number(id);

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    // 배틀 + 원본 방 정보 조회 (FOR UPDATE로 동시 클릭 직렬화)
    const [battles] = await conn.query<any[]>(
      `SELECT b.room_id, b.rematch_room_id, r.title
       FROM battles b
       JOIN rooms r ON r.id = b.room_id
       WHERE b.id = ? FOR UPDATE`,
      [battleId]
    );
    if (battles.length === 0) {
      await conn.rollback();
      return errorResponse('BATTLE_001', 404);
    }

    // 이 배틀 참가자인지 확인
    const [portfolios] = await conn.query<any[]>(
      'SELECT id FROM portfolios WHERE battle_id = ? AND user_id = ?',
      [battleId, auth.userId]
    );
    if (portfolios.length === 0) {
      await conn.rollback();
      return errorResponse('AUTH_003', 403);
    }

    const { rematch_room_id: rematchRoomId, title } = battles[0];

    // ── Case A: 이미 리매치 방이 만들어진 경우 ──
    if (rematchRoomId) {
      const [rematchRooms] = await conn.query<any[]>(
        'SELECT status FROM rooms WHERE id = ?',
        [rematchRoomId]
      );
      const rematchStatus = rematchRooms[0]?.status;

      if (!rematchStatus || rematchStatus === 'closed') {
        // 첫 번째 사람이 이미 나가버린 경우 → 로비로
        await conn.commit();
        return successResponse({ redirect: 'lobby' });
      }

      if (rematchStatus === 'active') {
        // 이미 새 배틀 시작됨
        const [newBattles] = await conn.query<any[]>(
          'SELECT id FROM battles WHERE room_id = ? ORDER BY started_at DESC LIMIT 1',
          [rematchRoomId]
        );
        await conn.commit();
        return successResponse({ redirect: 'battle', battleId: newBattles[0].id });
      }

      // rematchStatus === 'waiting' → 합류
      const [countRows] = await conn.query<any[]>(
        'SELECT COUNT(*) AS cnt FROM room_members WHERE room_id = ?',
        [rematchRoomId]
      );
      if (Number(countRows[0].cnt) >= 4) {
        await conn.rollback();
        return errorResponse('ROOM_002');
      }

      // INSERT IGNORE: 이미 들어가 있으면 그냥 무시
      await conn.query(
        'INSERT IGNORE INTO room_members (room_id, user_id) VALUES (?, ?)',
        [rematchRoomId, auth.userId]
      );
      await conn.commit();
      return successResponse({ redirect: 'room', roomId: rematchRoomId });
    }

    // ── Case B: 첫 번째 클릭 → 새 방 생성 ──
    const [newRoomResult] = await conn.query<any>(
      'INSERT INTO rooms (title, host_id) VALUES (?, ?)',
      [title, auth.userId]
    );
    const newRoomId = newRoomResult.insertId;

    await conn.query(
      'INSERT INTO room_members (room_id, user_id) VALUES (?, ?)',
      [newRoomId, auth.userId]
    );

    // 원본 배틀에 rematch_room_id 연결
    await conn.query(
      'UPDATE battles SET rematch_room_id = ? WHERE id = ?',
      [newRoomId, battleId]
    );

    await conn.commit();
    return successResponse({ redirect: 'room', roomId: newRoomId });
  } catch (e) {
    await conn.rollback();
    throw e;
  } finally {
    conn.release();
  }
}
