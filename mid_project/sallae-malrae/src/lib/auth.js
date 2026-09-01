import { verifyAccessToken } from '@/lib/jwt';

// accessToken HttpOnly 쿠키 검증 후 payload 반환, 실패 시 null.
export function getSessionUser(request) {
  const token = request.cookies.get('accessToken')?.value;
  if (!token) return null;

  try {
    return verifyAccessToken(token);
  } catch {
    return null;
  }
}
