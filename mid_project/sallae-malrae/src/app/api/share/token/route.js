import { randomBytes } from 'crypto';
import { query } from '@/lib/db';
import { getSessionUser } from '@/lib/auth';
import { successResponse, errorResponse } from '@/lib/response';

// GET /api/share/token — 기존 토큰 있으면 반환, 없으면 신규 발급.
export async function GET(request) {
  const user = getSessionUser(request);
  if (!user) return errorResponse('UNAUTHORIZED');

  try {
    const rows = await query('SELECT share_token FROM users WHERE id = ?', [user.user_id]);
    let shareToken = rows[0]?.share_token ?? null;

    if (!shareToken) {
      shareToken = randomBytes(8).toString('hex');
      await query('UPDATE users SET share_token = ? WHERE id = ?', [shareToken, user.user_id]);
    }

    const baseUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:3000';
    return successResponse(
      { shareToken, shareUrl: `${baseUrl}/share/${shareToken}` },
      'Share token retrieved.',
    );
  } catch {
    return errorResponse('INTERNAL_SERVER_ERROR');
  }
}
