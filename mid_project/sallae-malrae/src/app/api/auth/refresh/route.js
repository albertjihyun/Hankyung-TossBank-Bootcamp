import bcrypt from 'bcrypt';
import { query } from '@/lib/db';
import { verifyRefreshToken, signAccessToken, signRefreshToken } from '@/lib/jwt';
import { successResponse, errorResponse } from '@/lib/response';

const IS_PROD = process.env.NODE_ENV === 'production';

// POST /api/auth/refresh — RT 검증 후 새 AT + RT 발급 (Rotation).
// ADR-005: token_version 불일치 = 구 RT 재사용 시도 → 전 세션 강제 만료.
export async function POST(request) {
  const token = request.cookies.get('refreshToken')?.value;
  if (!token) return errorResponse('INVALID_REFRESH_TOKEN');

  let payload;
  try {
    payload = verifyRefreshToken(token);
  } catch {
    return errorResponse('INVALID_REFRESH_TOKEN');
  }

  const rows = await query(
    'SELECT id, refresh_token, token_version FROM users WHERE id = ?',
    [payload.user_id],
  );
  if (rows.length === 0) return errorResponse('INVALID_REFRESH_TOKEN');

  const user = rows[0];

  if (payload.token_version !== user.token_version) {
    await query(
      'UPDATE users SET token_version = token_version + 1, refresh_token = NULL WHERE id = ?',
      [user.id],
    );
    return errorResponse('TOKEN_REUSE_DETECTED');
  }

  if (!user.refresh_token) return errorResponse('INVALID_REFRESH_TOKEN');

  const match = await bcrypt.compare(token, user.refresh_token);
  if (!match) return errorResponse('INVALID_REFRESH_TOKEN');

  const newVersion = user.token_version + 1;
  const newPayload = { user_id: user.id, token_version: newVersion };
  const accessToken = signAccessToken(newPayload);
  const refreshToken = signRefreshToken(newPayload);

  const hashedRt = await bcrypt.hash(refreshToken, 10);
  await query(
    'UPDATE users SET refresh_token = ?, token_version = ? WHERE id = ?',
    [hashedRt, newVersion, user.id],
  );

  const response = successResponse(null, 'Token refreshed.');

  response.cookies.set('accessToken', accessToken, {
    httpOnly: true,
    maxAge: 7 * 24 * 60 * 60,
    path: '/',
    sameSite: 'strict',
    secure: IS_PROD,
  });

  response.cookies.set('refreshToken', refreshToken, {
    httpOnly: true,
    maxAge: 7 * 24 * 60 * 60,
    path: '/',
    sameSite: 'strict',
    secure: IS_PROD,
  });

  return response;
}
