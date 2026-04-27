import { NextRequest } from 'next/server';
import bcrypt from 'bcryptjs';
import pool from '@/lib/db';
import { signToken } from '@/lib/auth';
import { errorResponse, successResponse } from '@/lib/errors';

export async function POST(req: NextRequest) {
  const { email, nickname, password } = await req.json();

  if (!email || !nickname || !password) {
    return Response.json({ success: false, error: { code: 'INVALID', message: '필드를 모두 입력해주세요' } }, { status: 400 });
  }

  const conn = await pool.getConnection();
  try {
    const [emailRows] = await conn.query<any[]>(
      'SELECT id FROM users WHERE email = ?', [email]
    );
    if (emailRows.length > 0) return errorResponse('AUTH_EMAIL_EXISTS');

    const [nickRows] = await conn.query<any[]>(
      'SELECT id FROM users WHERE nickname = ?', [nickname]
    );
    if (nickRows.length > 0) return errorResponse('AUTH_NICK_EXISTS');

    const passwordHash = await bcrypt.hash(password, 10);
    let result: any;
    try {
      [result] = await conn.query<any>(
        'INSERT INTO users (email, nickname, password_hash) VALUES (?, ?, ?)',
        [email, nickname, passwordHash]
      );
    } catch (e: any) {
      if (e.code === 'ER_DUP_ENTRY') {
        return errorResponse('AUTH_EMAIL_EXISTS');
      }
      throw e;
    }

    const userId = result.insertId;
    const token = await signToken({ userId, nickname, email });

    const res = successResponse({ token, user: { id: userId, email, nickname } }, 201);
    res.headers.set(
      'Set-Cookie',
      `${process.env.TOKEN_COOKIE_NAME || 'token'}=${token}; Path=/; HttpOnly; Max-Age=604800; SameSite=Lax`
    );
    return res;
  } finally {
    conn.release();
  }
}
