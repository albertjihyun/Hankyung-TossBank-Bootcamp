import { NextResponse } from 'next/server';
import { ERRORS } from '@/constants/errors';

// 성공 응답 — { success: true, data, message }
// (개발 규칙 §4, API 관련 논의사항 §공통 규칙)
export function successResponse(data, message = '', status = 200) {
  return NextResponse.json(
    { success: true, data, message },
    { status },
  );
}

// 실패 응답 — { success: false, data: null, message, code }
// code 한 개만 받아 ERRORS에서 message·status를 자동 매핑 → 호출 측 일관성 강제.
// ERRORS에 없는 키를 넘기면 의도적으로 죽음 (개발자 오타를 명확히 드러내기 위함).
export function errorResponse(code) {
  const def = ERRORS[code];
  return NextResponse.json(
    { success: false, data: null, message: def.message, code },
    { status: def.status },
  );
}
