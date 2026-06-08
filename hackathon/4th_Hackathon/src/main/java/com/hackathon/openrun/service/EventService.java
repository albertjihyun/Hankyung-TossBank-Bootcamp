package com.hackathon.openrun.service;

import com.hackathon.openrun.domain.Category;
import com.hackathon.openrun.domain.Event;
import com.hackathon.openrun.domain.EventStatus;
import com.hackathon.openrun.domain.Member;
import com.hackathon.openrun.dto.EventCreateRequest;
import com.hackathon.openrun.exception.ForbiddenException;
import com.hackathon.openrun.exception.NotFoundException;
import com.hackathon.openrun.repository.EventRepository;
import com.hackathon.openrun.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 조회/등록/상태전이 (TECH_SPEC §4 비동시성 영역 + 스케줄러).
 * 좌석 경합(예약/취소)은 ReservationService 가 담당.
 */
@Service
@Transactional(readOnly = true)
public class EventService {

    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;

    public EventService(EventRepository eventRepository, MemberRepository memberRepository) {
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
    }

    /** 홈 피드: tab(scheduled/open/closed) + category 필터. (TECH_SPEC §5 GET /) */
    public List<Event> findFeed(String tab, Category category) {
        EventStatus status = parseTab(tab);
        if (status != null && category != null) {
            return eventRepository.findByStatusAndCategoryOrderByOpenAtAsc(status, category);
        }
        if (status != null) {
            return eventRepository.findByStatusOrderByOpenAtAsc(status);
        }
        if (category != null) {
            return eventRepository.findByCategoryOrderByOpenAtAsc(category);
        }
        return eventRepository.findAllByOrderByOpenAtAsc();
    }

    public Event getEvent(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 행사입니다."));
    }

    public List<Event> findByHost(Long hostId) {
        return eventRepository.findByHostIdOrderByCreatedAtDesc(hostId);
    }

    /** 모든 행사 (ADMIN). */
    public List<Event> findAll() {
        return eventRepository.findAllByOrderByOpenAtAsc();
    }

    @Transactional
    public Long create(Long hostId, EventCreateRequest req) {
        Member host = memberRepository.findById(hostId)
                .orElseThrow(() -> new NotFoundException("호스트를 찾을 수 없습니다."));
        // open_at 이 과거가 아니면 SCHEDULED, (이론상) 이미 지났으면 즉시 OPEN
        EventStatus initial = req.getOpenAt().isAfter(LocalDateTime.now())
                ? EventStatus.SCHEDULED : EventStatus.OPEN;
        Event event = Event.builder()
                .host(host)
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory())
                .capacity(req.getCapacity())
                .openAt(req.getOpenAt())
                .status(initial)
                .build();
        return eventRepository.save(event).getId();
    }

    /** HOST 본인 행사인지 검증 후 반환. */
    public Event getOwnedEvent(Long eventId, Long hostId) {
        Event event = getEvent(eventId);
        if (!event.getHost().getId().equals(hostId)) {
            throw new ForbiddenException("본인 행사만 조회할 수 있습니다.");
        }
        return event;
    }

    /** ADMIN: 행사 강제 마감. */
    @Transactional
    public void closeByAdmin(Long eventId) {
        Event event = getEvent(eventId);
        event.close();
    }

    /**
     * 스케줄러: open_at 도래한 SCHEDULED 행사를 OPEN 으로 전이. (TECH_SPEC §3)
     * ReservationScheduler 가 주기 호출.
     */
    @Transactional
    public int openDueEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> due = eventRepository.findByStatusAndOpenAtLessThanEqual(EventStatus.SCHEDULED, now);
        due.forEach(Event::open);
        return due.size();
    }

    private EventStatus parseTab(String tab) {
        if (tab == null || tab.isBlank()) {
            return null;
        }
        return switch (tab.toLowerCase()) {
            case "scheduled" -> EventStatus.SCHEDULED;
            case "open" -> EventStatus.OPEN;
            case "closed" -> EventStatus.CLOSED;
            default -> null;
        };
    }
}
