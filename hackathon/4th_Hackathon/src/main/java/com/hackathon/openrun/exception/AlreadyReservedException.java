package com.hackathon.openrun.exception;

/** 1인 1예약 위반(이미 예약/대기 중) → 409. */
public class AlreadyReservedException extends OpenrunException {
    public AlreadyReservedException(String message) {
        super(message);
    }
}
