package com.hackathon.openrun.controller;

import com.hackathon.openrun.domain.Category;
import com.hackathon.openrun.dto.EventCreateRequest;
import com.hackathon.openrun.security.MemberPrincipal;
import com.hackathon.openrun.service.EventService;
import com.hackathon.openrun.service.ReservationService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * HOST 전용: 행사 목록/등록/참가자 현황 (TECH_SPEC §5, 권한 hasRole("HOST")).
 */
@Controller
@RequestMapping("/host")
public class HostController {

    private final EventService eventService;
    private final ReservationService reservationService;

    public HostController(EventService eventService, ReservationService reservationService) {
        this.eventService = eventService;
        this.reservationService = reservationService;
    }

    @GetMapping("/events")
    public String myEvents(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("events", eventService.findByHost(principal.getMemberId()));
        return "host/events";
    }

    @GetMapping("/events/new")
    public String newEventForm(Model model) {
        if (!model.containsAttribute("eventCreateRequest")) {
            model.addAttribute("eventCreateRequest", new EventCreateRequest());
        }
        model.addAttribute("categories", Category.values());
        return "host/event-form";
    }

    @PostMapping("/events")
    public String createEvent(@Valid @ModelAttribute EventCreateRequest eventCreateRequest,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal MemberPrincipal principal,
                              Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("categories", Category.values());
            return "host/event-form";
        }
        eventService.create(principal.getMemberId(), eventCreateRequest);
        return "redirect:/host/events";
    }

    @GetMapping("/events/{id}")
    public String eventDetail(@PathVariable Long id,
                              @AuthenticationPrincipal MemberPrincipal principal,
                              Model model) {
        model.addAttribute("event", eventService.getOwnedEvent(id, principal.getMemberId()));
        model.addAttribute("reservations", reservationService.findEventReservations(id));
        return "host/event-detail";
    }
}
