package com.hackathon.openrun.domain;

/**
 * 행사 상태 (TECH_SPEC §3 상태 전이).
 * SCHEDULED → (open_at 도달/관리자) OPEN → (정원/마감) CLOSED
 */
public enum EventStatus {
    SCHEDULED("오픈예정"),
    OPEN("오픈중"),
    CLOSED("마감");

    private final String label;

    EventStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
