package com.jarvis.global.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** guest_id 쿠키 — 2시간 sliding·반납 (GUEST-LIFECYCLE · 노션 E-1 2026-07-31) */
class GuestCookieManagerTest {

    private static final String GUEST_ID = "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b";

    private final GuestCookieManager manager = new GuestCookieManager(true);

    @Test
    @DisplayName("resolve — 서버가 발급한 형식(UUID)이면 그대로 읽는다")
    void resolveAcceptsIssuedFormat() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.setCookies(new Cookie(GuestCookieManager.COOKIE_NAME, GUEST_ID));

        assertThat(manager.resolve(request)).contains(GUEST_ID);
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "[{0}]")
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "x|SELLER|5", "g-uuid", "", "1", "3F2B8C1E-9A4D-4E6F-8B7A-1C2D3E4F5A6B",
            "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b|SELLER|5"})
    @DisplayName("resolve — 형식이 다른 값은 쿠키가 없는 것으로 본다(클라이언트가 지어낸 신원 거부)")
    void resolveRejectsForgedValue(String forged) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.setCookies(new Cookie(GuestCookieManager.COOKIE_NAME, forged));

        assertThat(manager.resolve(request)).isEmpty();
    }

    @Test
    @DisplayName("발급 — Max-Age 2시간, HttpOnly·Secure·SameSite=Lax")
    void writeSetsTwoHourCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.write(response, "g-uuid");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("guest_id=g-uuid")
                .contains("Max-Age=7200")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax");
    }

    @Test
    @DisplayName("반납 — Max-Age=0으로 즉시 만료시켜 그 구간을 끝낸다")
    void clearExpiresCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.clear(response);

        assertThat(response.getHeader("Set-Cookie")).contains("guest_id=").contains("Max-Age=0");
    }

    @Test
    @DisplayName("sliding — 쿠키가 있으면 같은 값으로 다시 내려 2시간을 민다")
    void slidingFilterExtendsExistingCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        request.setCookies(new Cookie(GuestCookieManager.COOKIE_NAME, GUEST_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GuestCookieSlidingFilter(manager).doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("Set-Cookie")).contains("guest_id=" + GUEST_ID).contains("Max-Age=7200");
    }

    @Test
    @DisplayName("sliding — 쿠키가 없으면 발급하지 않는다(발급은 도메인 서비스 소관, 03 D3)")
    void slidingFilterDoesNotIssue() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GuestCookieSlidingFilter(manager).doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    @DisplayName("sliding — /api/auth/는 제외한다(승계가 쿠키를 반납하는 자리라 연장하면 안 된다)")
    void slidingFilterSkipsAuthPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setCookies(new Cookie(GuestCookieManager.COOKIE_NAME, GUEST_ID));
        MockHttpServletResponse response = new MockHttpServletResponse();

        new GuestCookieSlidingFilter(manager).doFilter(request, response, (req, res) -> { });

        assertThat(response.getHeader("Set-Cookie")).isNull();
    }
}
