import { NextResponse } from 'next/server';
import { query } from '@/lib/db';
import { getSessionUser } from '@/lib/auth';

const IS_PROD = process.env.NODE_ENV === 'production';

// POST /api/auth/logout — RT DB 삭제 + AT/RT 쿠키 제거.
// AT 만료 여부와 관계없이 항상 쿠키를 제거해 브라우저에 RT가 남지 않도록 함.
// AT가 유효한 경우에만 DB의 RT도 무효화(AT 없이는 user_id를 신뢰할 수 없음).
export async function POST(request) {
  const payload = getSessionUser(request);

  if (payload) {
    await query('UPDATE users SET refresh_token = NULL WHERE id = ?', [payload.user_id]);
  }

  const response = new NextResponse(null, { status: 204 });

  response.cookies.set('accessToken', '', {
    httpOnly: true,
    maxAge: 0,
    path: '/',
    sameSite: 'strict',
    secure: IS_PROD,
  });

  response.cookies.set('refreshToken', '', {
    httpOnly: true,
    maxAge: 0,
    path: '/',
    sameSite: 'strict',
    secure: IS_PROD,
  });

  return response;
}
