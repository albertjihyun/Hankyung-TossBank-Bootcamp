package com.hackathon.olive.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

/** 인증 토큰을 httpOnly 쿠키로 내려/지운다. (BFF가 브라우저↔Spring 사이를 그대로 중계) */
public final class CookieUtil {
    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";

    private CookieUtil() {}

    public static void setAuthCookies(HttpServletResponse res,
                                      String access, long accessMaxAge,
                                      String refresh, long refreshMaxAge) {
        addCookie(res, ACCESS, access, accessMaxAge);
        addCookie(res, REFRESH, refresh, refreshMaxAge);
    }

    public static void setAccessCookie(HttpServletResponse res, String access, long accessMaxAge) {
        addCookie(res, ACCESS, access, accessMaxAge);
    }

    public static void clearAuthCookies(HttpServletResponse res) {
        addCookie(res, ACCESS, "", 0);
        addCookie(res, REFRESH, "", 0);
    }

    private static void addCookie(HttpServletResponse res, String name, String value, long maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(false)       // 로컬 http + cloudflared https 양쪽에서 동작하도록(데모)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public static String readCookie(HttpServletRequest req, String name) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
