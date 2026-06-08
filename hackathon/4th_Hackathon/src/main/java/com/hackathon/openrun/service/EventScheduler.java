package com.hackathon.openrun.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * open_at 도래 행사를 SCHEDULED→OPEN 으로 전이시키는 스케줄러 (TECH_SPEC §3 상태 전이).
 *
 * <p>다중 인스턴스 주의: 두 인스턴스가 동시에 돌아도 상태 전이는 멱등(이미 OPEN 이면 대상 제외)이라
 * 정합성에 영향 없음. (운영 규모라면 ShedLock 등으로 단일화 — README 확장 설계 참고)
 */
@Component
public class EventScheduler {

    private final EventService eventService;

    public EventScheduler(EventService eventService) {
        this.eventService = eventService;
    }

    /** 1초마다 오픈 시각이 도래한 행사를 오픈 처리(카운트다운 데모 대응). */
    @Scheduled(fixedDelay = 1000)
    public void openDueEvents() {
        eventService.openDueEvents();
    }
}
