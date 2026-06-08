package com.hackathon.openrun.controller;

import com.hackathon.openrun.domain.ReservationStatus;
import com.hackathon.openrun.security.MemberPrincipal;
import com.hackathon.openrun.service.EventService;
import com.hackathon.openrun.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 행사 상세 + 선착순 예약 처리 (TECH_SPEC §5).
 */
@Controller
public class EventController {

    private final EventService eventService;
    private final ReservationService reservationService;

    public EventController(EventService eventService, ReservationService reservationService) {
        this.eventService = eventService;
        this.reservationService = reservationService;
    }

    @GetMapping("/events/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         Model model) {
        model.addAttribute("event", eventService.getEvent(id));
        // 내 신청 상태(있으면 버튼 비활성화 표시용)
        if (principal != null) {
            reservationService.findMyReservations(principal.getMemberId()).stream()
                    .filter(r -> r.getEvent().getId().equals(id) && r.isActive())
                    .findFirst()
                    .ifPresent(r -> model.addAttribute("myReservationStatus", r.getStatus().name()));
        }
        return "event/detail";
    }

    @PostMapping("/events/{id}/reserve")
    public String reserve(@PathVariable Long id,
                          @AuthenticationPrincipal MemberPrincipal principal,
                          RedirectAttributes ra) {
        ReservationStatus result = reservationService.reserve(id, principal.getMemberId());
        if (result == ReservationStatus.RESERVED) {
            ra.addFlashAttribute("message", "예약이 확정되었습니다! 🎉");
        } else {
            ra.addFlashAttribute("message", "정원이 가득 차 대기열에 등록되었습니다.");
        }
        return "redirect:/events/" + id;
    }
}
