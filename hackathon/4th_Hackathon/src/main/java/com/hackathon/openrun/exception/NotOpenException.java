package com.hackathon.openrun.exception;

/** 아직 오픈되지 않았거나 마감된 행사에 예약 시도 → 409. */
public class NotOpenException extends OpenrunException {
    public NotOpenException(String message) {
        super(message);
    }
}
