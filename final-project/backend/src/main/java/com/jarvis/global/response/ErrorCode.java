package com.jarvis.global.response;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드 목록의 원본은 04 §11 — 구현 중 추가 시 문서에도 반영한다.
 * 형식: <도메인>_<사유> 대문자 스네이크, message는 사용자 노출 가능한 한국어 문장 (03 D2).
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // auth (04 §11 · 03 D2 — 401 2종 분리: 토큰 없음 vs 만료)
    AUTH_REQUIRED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증이 만료되었습니다."),
    AUTH_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // member
    MEMBER_EMAIL_DUPLICATE(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");

    private final HttpStatus status;
    private final String message;
}
