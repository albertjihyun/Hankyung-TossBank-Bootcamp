package com.hackathon.openrun.controller;

import com.hackathon.openrun.security.MemberPrincipal;
import com.hackathon.openrun.service.ReservationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 내 예약/대기 현황 + 취소 (TECH_SPEC §5).
 */
@Controller
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/me/reservations")
    public String myReservations(@AuthenticationPrincipal MemberPrincipal principal, Model model) {
        model.addAttribute("reservations", reservationService.findMyReservations(principal.getMemberId()));
        return "me/reservations";
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancel(@PathVariable Long id,
                         @AuthenticationPrincipal MemberPrincipal principal,
                         RedirectAttributes ra) {
        reservationService.cancel(id, principal.getMemberId());
        ra.addFlashAttribute("message", "예약이 취소되었습니다. 대기자가 있으면 자동 승계됩니다.");
        return "redirect:/me/reservations";
    }
}
