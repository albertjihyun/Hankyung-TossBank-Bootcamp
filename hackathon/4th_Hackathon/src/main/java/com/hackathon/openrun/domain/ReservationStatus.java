package com.hackathon.openrun.domain;

/**
 * 예약 상태 (TECH_SPEC §3).
 * RESERVED | WAITING → CANCELLED ; 취소 시 WAITING → RESERVED 승계
 */
public enum ReservationStatus {
    RESERVED("예약확정"),
    WAITING("대기"),
    CANCELLED("취소");

    private final String label;

    ReservationStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
