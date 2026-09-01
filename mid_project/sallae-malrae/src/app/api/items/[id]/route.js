import { query } from '@/lib/db';
import { getSessionUser } from '@/lib/auth';
import { errorResponse } from '@/lib/response';
import { ITEM_STATUS } from '@/constants/status';

// DELETE /api/items/:id — WAITING 상태 항목만 삭제 (204 No Content)
export async function DELETE(request, { params }) {
  const user = getSessionUser(request);
  if (!user) return errorResponse('UNAUTHORIZED');

  const id = Number((await params).id);

  let rows;
  try {
    rows = await query('SELECT id, user_id, status FROM items WHERE id = ?', [id]);
  } catch {
    return errorResponse('SERVER_ERROR');
  }
  if (rows.length === 0) return errorResponse('ITEM_NOT_FOUND');

  const item = rows[0];
  if (item.user_id !== user.user_id) return errorResponse('NOT_OWNER');
  if (item.status !== ITEM_STATUS.WAITING) return errorResponse('CANNOT_DELETE_DECIDED');

  try {
    await query('DELETE FROM items WHERE id = ?', [id]);
  } catch {
    return errorResponse('SERVER_ERROR');
  }

  return new Response(null, { status: 204 });
}
