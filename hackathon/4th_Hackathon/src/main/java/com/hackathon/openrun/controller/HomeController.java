package com.hackathon.openrun.controller;

import com.hackathon.openrun.domain.Category;
import com.hackathon.openrun.service.EventService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 홈 피드 (TECH_SPEC §5 GET /). 컨트롤러는 위임만 — 로직 없음.
 */
@Controller
public class HomeController {

    private final EventService eventService;

    public HomeController(EventService eventService) {
        this.eventService = eventService;
    }

    @GetMapping("/")
    public String home(@RequestParam(required = false) String tab,
                       @RequestParam(required = false) Category category,
                       Model model) {
        model.addAttribute("events", eventService.findFeed(tab, category));
        model.addAttribute("categories", Category.values());
        model.addAttribute("selectedTab", tab);
        model.addAttribute("selectedCategory", category);
        return "index";
    }
}
