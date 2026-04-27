import { NextRequest } from 'next/server';
import bcrypt from 'bcryptjs';
import pool from '@/lib/db';
import { signToken } from '@/lib/auth';
import { errorResponse, successResponse } from '@/lib/errors';

export async function POST(req: NextRequest) {
  const { email, password } = await req.json();

  const conn = await pool.getConnection();
  try {
    const [rows] = await conn.query<any[]>(
      'SELECT id, email, nickname, password_hash FROM users WHERE email = ?',
      [email]
    );

    if (rows.length === 0) return errorResponse('AUTH_INVALID', 401);

    const user = rows[0];
    const valid = await bcrypt.compare(password, user.password_hash);
    if (!valid) return errorResponse('AUTH_INVALID', 401);

    const token = await signToken({ userId: user.id, nickname: user.nickname, email: user.email });

    const res = successResponse({ token, user: { id: user.id, email: user.email, nickname: user.nickname } });
    res.headers.set(
      'Set-Cookie',
      `${process.env.TOKEN_COOKIE_NAME || 'token'}=${token}; Path=/; HttpOnly; Max-Age=604800; SameSite=Lax`
    );
    return res;
  } finally {
    conn.release();
  }
}
