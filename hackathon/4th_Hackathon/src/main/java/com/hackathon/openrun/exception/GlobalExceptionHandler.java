package com.hackathon.openrun.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 전역 예외 처리 (TECH_SPEC §10). 도메인 예외를 사용자 친화 메시지 + 적절한 뷰/상태로 변환.
 * /api/** 요청은 JSON 상태코드만 반환하도록 분기.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    /** 미존재 → 404 페이지. */
    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NotFoundException e, HttpServletRequest req, Model model) {
        model.addAttribute("message", e.getMessage());
        model.addAttribute("path", req.getRequestURI());
        return "error/404";
    }

    /** 권한 없음(본인 리소스 아님 등) → 403 페이지. */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleForbidden(ForbiddenException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/403";
    }

    /**
     * 오픈 전/마감/중복 예약 등 비즈니스 충돌 → 409, 친화적 안내 페이지.
     * (정원 마감·중복 신청 시 사용자 친화 안내 — TECH_SPEC §10)
     */
    @ExceptionHandler({NotOpenException.class, AlreadyReservedException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleConflict(OpenrunException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/conflict";
    }

    /** 잘못된 입력(역할 파싱 등). */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e, Model model) {
        model.addAttribute("message", e.getMessage());
        return "error/conflict";
    }
}
