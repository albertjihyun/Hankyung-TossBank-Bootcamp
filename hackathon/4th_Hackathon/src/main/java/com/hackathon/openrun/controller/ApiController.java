package com.hackathon.openrun.controller;

import com.hackathon.openrun.dto.SeatStatusResponse;
import com.hackathon.openrun.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 폴링용 JSON API (TECH_SPEC §0/§5). 상세 페이지 JS 가 N초마다 잔여석 조회.
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final EventService eventService;

    public ApiController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/events/{id}/seats")
    public SeatStatusResponse seats(@PathVariable Long id) {
        return SeatStatusResponse.from(eventService.getEvent(id));
    }
}
