package com.jarvis.global.response;

import lombok.Getter;

/**
 * 도메인별 커스텀 예외의 공통 부모 — GlobalExceptionHandler가 ErrorCode로 매핑한다 (03 §6).
 * 컨트롤러 try-catch 금지, 서비스 레이어에서 던진다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
