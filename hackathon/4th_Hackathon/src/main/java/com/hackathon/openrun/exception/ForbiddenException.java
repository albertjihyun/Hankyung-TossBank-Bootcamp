package com.hackathon.openrun.exception;

/** 본인 소유가 아닌 리소스 조작 시도 등 → 403. */
public class ForbiddenException extends OpenrunException {
    public ForbiddenException(String message) {
        super(message);
    }
}
