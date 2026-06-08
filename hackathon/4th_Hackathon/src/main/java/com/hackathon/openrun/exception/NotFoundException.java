package com.hackathon.openrun.exception;

/** 행사/예약 등 리소스 미존재 → 404. */
public class NotFoundException extends OpenrunException {
    public NotFoundException(String message) {
        super(message);
    }
}
