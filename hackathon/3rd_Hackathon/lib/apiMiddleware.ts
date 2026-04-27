import { NextRequest } from 'next/server';
import { verifyToken, getTokenFromHeader, JwtPayload } from './auth';
import { errorResponse } from './errors';

export async function authenticate(req: NextRequest): Promise<JwtPayload | Response> {
  const token =
    getTokenFromHeader(req.headers.get('authorization')) ??
    req.cookies.get(process.env.TOKEN_COOKIE_NAME || 'token')?.value ??
    null;

  if (!token) return errorResponse('AUTH_001', 401);

  const payload = await verifyToken(token);
  if (!payload) return errorResponse('AUTH_002', 401);

  return payload;
}

export function isResponse(val: unknown): val is Response {
  return val instanceof Response;
}
