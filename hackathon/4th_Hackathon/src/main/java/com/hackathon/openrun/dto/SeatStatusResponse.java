package com.hackathon.openrun.dto;

import com.hackathon.openrun.domain.Event;

/**
 * 잔여석 폴링 응답 (TECH_SPEC §5 GET /api/events/{id}/seats).
 * {@code {remaining, reserved, capacity, status, openAt}}
 */
public record SeatStatusResponse(
        Long eventId,
        int remaining,
        int reserved,
        int capacity,
        String status,
        String openAt
) {
    public static SeatStatusResponse from(Event e) {
        return new SeatStatusResponse(
                e.getId(),
                e.getRemaining(),
                e.getReservedCount(),
                e.getCapacity(),
                e.getStatus().name(),
                e.getOpenAt().toString()
        );
    }
}
