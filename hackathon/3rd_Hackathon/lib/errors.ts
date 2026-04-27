export const ErrorCodes = {
  AUTH_EMAIL_EXISTS: { code: 'AUTH_EMAIL_EXISTS', message: '이미 사용 중인 이메일이에요' },
  AUTH_NICK_EXISTS:  { code: 'AUTH_NICK_EXISTS',  message: '이미 사용 중인 닉네임이에요' },
  AUTH_INVALID:      { code: 'AUTH_INVALID',      message: '이메일 또는 비밀번호가 틀려요' },
  AUTH_001:          { code: 'AUTH_001',          message: '로그인이 필요해요' },
  AUTH_002:          { code: 'AUTH_002',          message: '토큰이 만료됐어요' },
  AUTH_003:          { code: 'AUTH_003',          message: '권한이 없어요' },
  ROOM_001:          { code: 'ROOM_001',          message: '방을 찾을 수 없어요' },
  ROOM_002:          { code: 'ROOM_002',          message: '이미 꽉 찬 방이에요' },
  ROOM_003:          { code: 'ROOM_003',          message: '이미 참가 중인 방이 있어요' },
  ROOM_004:          { code: 'ROOM_004',          message: '이미 시작된 배틀이에요' },
  ROOM_005:          { code: 'ROOM_005',          message: '인원이 부족해요 (2명 이상 필요)' },
  BATTLE_001:        { code: 'BATTLE_001',        message: '배틀을 찾을 수 없어요' },
  BATTLE_002:        { code: 'BATTLE_002',        message: '배틀이 종료됐어요' },
  BATTLE_003:        { code: 'BATTLE_003',        message: '잔액이 부족해요' },
  BATTLE_004:        { code: 'BATTLE_004',        message: '보유 주식이 부족해요' },
  BATTLE_EXISTS:     { code: 'BATTLE_EXISTS',     message: '이미 배틀이 생성됐어요' },
} as const;

export type ErrorCode = keyof typeof ErrorCodes;

export function errorResponse(code: ErrorCode, status = 400) {
  return Response.json(
    { success: false, error: ErrorCodes[code] },
    { status }
  );
}

export function successResponse<T>(data: T, status = 200) {
  return Response.json({ success: true, data }, { status });
}
