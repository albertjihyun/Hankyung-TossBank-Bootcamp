package com.hackathon.openrun.exception;

/**
 * 도메인 예외 베이스. 사용자 친화 메시지를 담는다. (TECH_SPEC §10)
 */
public abstract class OpenrunException extends RuntimeException {
    protected OpenrunException(String message) {
        super(message);
    }
}
