package com.hackathon.olive.web;

import com.hackathon.olive.dto.AuthDto.*;
import com.hackathon.olive.security.CookieUtil;
import com.hackathon.olive.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.access-token-validity-seconds}")
    private long accessMaxAge;
    @Value("${jwt.refresh-token-validity-seconds}")
    private long refreshMaxAge;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse signup(@Valid @RequestBody SignupRequest req) {
        return authService.signup(req);
    }

    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest req,
                              HttpSession session, HttpServletResponse res) {
        TokenResponse tokens = authService.login(req, session);
        CookieUtil.setAuthCookies(res, tokens.accessToken(), accessMaxAge,
                tokens.refreshToken(), refreshMaxAge);
        return tokens.user();
    }

    /** 액세스 토큰 만료 시 리프레시 쿠키로 재발급 + 새 액세스 쿠키 설정. */
    @PostMapping("/refresh")
    public UserResponse refresh(HttpServletRequest req, HttpServletResponse res) {
        String refresh = CookieUtil.readCookie(req, CookieUtil.REFRESH);
        TokenResponse tokens = authService.refresh(refresh);
        CookieUtil.setAccessCookie(res, tokens.accessToken(), accessMaxAge);
        return tokens.user();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest req, HttpServletResponse res, HttpSession session) {
        String token = CookieUtil.readCookie(req, CookieUtil.ACCESS);
        if (token == null) {
            String h = req.getHeader("Authorization");
            if (h != null && h.startsWith("Bearer ")) token = h.substring(7);
        }
        authService.logout(token, session);
        CookieUtil.clearAuthCookies(res);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Long userId) {
        if (userId == null) throw ApiException.unauthorized("로그인이 필요합니다.");
        return authService.me(userId);
    }
}
