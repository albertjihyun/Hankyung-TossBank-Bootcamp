import bcrypt from 'bcrypt';
import { query } from '@/lib/db';
import { successResponse, errorResponse } from '@/lib/response';
import { checkRateLimit } from '@/lib/rateLimit';
import {
  isValidEmail,
  isValidPasswordLength,
  isValidNicknameLength,
} from '@/lib/validators';

// POST /api/auth/signup — 계정 생성.
// AT/RT 발급은 login에서 (signup은 계정 생성만 — MVP 문서·기획서·API 명세서 일관).
//
// 중복 처리 — ADR-004 다층 방어선:
//   1차: 코드 레벨 SELECT 선검사 (race-free 케이스에서 사용자 친화 응답)
//   2차: DB uk_users_email / uk_users_nickname UNIQUE (코드 race condition 최종 방어선)
export async function POST(request) {
  const ip = request.headers.get('x-forwarded-for')?.split(',')[0]?.trim()
    ?? request.headers.get('x-real-ip')
    ?? 'unknown';
  if (!checkRateLimit(ip, 'signup', 5, 60 * 60 * 1000)) {
    return errorResponse('RATE_LIMITED');
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return errorResponse('REQUIRED_FIELD');
  }

  const { email, password, nickname } = body ?? {};

  if (!email || !password || !nickname) {
    return errorResponse('REQUIRED_FIELD');
  }

  if (!isValidEmail(email)) return errorResponse('INVALID_EMAIL');
  if (!isValidPasswordLength(password)) return errorResponse('PASSWORD_TOO_SHORT');
  if (!isValidNicknameLength(nickname)) return errorResponse('REQUIRED_FIELD');

  // 중복 1차 검사 (SELECT)
  const emailRows = await query('SELECT id FROM users WHERE email = ?', [email]);
  if (emailRows.length > 0) return errorResponse('DUPLICATE_EMAIL');

  const nicknameRows = await query('SELECT id FROM users WHERE nickname = ?', [nickname]);
  if (nicknameRows.length > 0) return errorResponse('DUPLICATE_NICKNAME');

  // bcrypt 해시 — saltRounds 10 (업계 관례, ADR-005 인증 방어선)
  const hashed = await bcrypt.hash(password, 10);

  // INSERT — UNIQUE 충돌(ER_DUP_ENTRY 1062)은 race condition 중복으로 간주
  let result;
  try {
    result = await query(
      'INSERT INTO users (email, password, nickname) VALUES (?, ?, ?)',
      [email, hashed, nickname],
    );
  } catch (err) {
    if (err.code === 'ER_DUP_ENTRY' || err.errno === 1062) {
      // err.message 예시: "Duplicate entry 'xxx' for key 'uk_users_nickname'"
      if (err.message?.includes('uk_users_nickname')) return errorResponse('DUPLICATE_NICKNAME');
      return errorResponse('DUPLICATE_EMAIL');
    }
    throw err;
  }

  return successResponse(
    { user_id: result.insertId, email, nickname },
    'Signup successful.',
    201,
  );
}
