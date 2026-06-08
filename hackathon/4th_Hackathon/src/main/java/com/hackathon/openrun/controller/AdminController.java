package com.hackathon.openrun.controller;

import com.hackathon.openrun.service.EventService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * ADMIN 전용: 전체 행사 관리 + 강제 마감 (TECH_SPEC §5, 권한 hasRole("ADMIN")).
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final EventService eventService;

    public AdminController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("events", eventService.findAll());
        return "admin/dashboard";
    }

    @PostMapping("/events/{id}/close")
    public String closeEvent(@PathVariable Long id, RedirectAttributes ra) {
        eventService.closeByAdmin(id);
        ra.addFlashAttribute("message", "행사를 마감 처리했습니다.");
        return "redirect:/admin";
    }
}
