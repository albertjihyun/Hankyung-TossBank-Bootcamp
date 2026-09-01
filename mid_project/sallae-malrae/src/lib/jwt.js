import jwt from 'jsonwebtoken';
import * as jose from 'jose';

export function signAccessToken(payload) {
  return jwt.sign(payload, process.env.JWT_ACCESS_SECRET, { expiresIn: '15m' });
}

export function signRefreshToken(payload) {
  return jwt.sign(payload, process.env.JWT_REFRESH_SECRET, { expiresIn: '7d' });
}

export function verifyAccessToken(token) {
  return jwt.verify(token, process.env.JWT_ACCESS_SECRET);
}

export function verifyRefreshToken(token) {
  return jwt.verify(token, process.env.JWT_REFRESH_SECRET);
}

// Edge Runtime용 (middleware) — jose 기반
export async function verifyAccessTokenEdge(token) {
  const secret = new TextEncoder().encode(process.env.JWT_ACCESS_SECRET);
  const { payload } = await jose.jwtVerify(token, secret);
  return payload;
}
