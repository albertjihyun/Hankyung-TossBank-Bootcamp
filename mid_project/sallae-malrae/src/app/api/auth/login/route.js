import bcrypt from 'bcrypt';
import { query } from '@/lib/db';
import { signAccessToken, signRefreshToken } from '@/lib/jwt';
import { successResponse, errorResponse } from '@/lib/response';
import { checkRateLimit } from '@/lib/rateLimit';

const IS_PROD = process.env.NODE_ENV === 'production';

// POST /api/auth/login — AT(15분) + RT(7일) 발급.
// ADR-005: RT는 bcrypt 해시로 users.refresh_token 저장 + HttpOnly 쿠키 전달.
// 이메일 없음/비밀번호 불일치 모두 LOGIN_FAILED — 어느 쪽인지 노출하지 않음(enum 방지).
export async function POST(request) {
  const ip = request.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
    ?? request.headers.get('x-real-ip')
    ?? 'unknown';
  if (!checkRateLimit(ip, 'login', 10, 15 * 60 * 1000)) {
    return errorResponse('RATE_LIMITED');
  }
  let body;
  try {
    body = await request.json();
  } catch {
    return errorResponse('REQUIRED_FIELD');
  }

  const { email, password } = body ?? {};

  if (!email || !password) return errorResponse('REQUIRED_FIELD');

  const rows = await query(
    'SELECT id, password, nickname, level, token_version FROM users WHERE email = ?',
    [email],
  );
  if (rows.length === 0) return errorResponse('LOGIN_FAILED');

  const user = rows[0];

  const passwordMatch = await bcrypt.compare(password, user.password);
  if (!passwordMatch) return errorResponse('LOGIN_FAILED');

  const payload = { user_id: user.id, token_version: user.token_version };
  const accessToken = signAccessToken(payload);
  const refreshToken = signRefreshToken(payload);

  const hashedRt = await bcrypt.hash(refreshToken, 10);
  await query('UPDATE users SET refresh_token = ? WHERE id = ?', [hashedRt, user.id]);

  const response = successResponse(
    { user: { user_id: user.id, nickname: user.nickname, level: user.level } },
    'Login successful.',
  );

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
