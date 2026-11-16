package com.jarvis.internal;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jarvis.global.auth.AccessCookieManager;
import com.jarvis.global.auth.ClientIp;
import com.jarvis.global.auth.EnvelopeAccessDeniedHandler;
import com.jarvis.global.auth.EnvelopeAuthenticationEntryPoint;
import com.jarvis.global.auth.GuestCookieManager;
import com.jarvis.global.auth.JwtAuthenticationFilter;
import com.jarvis.global.auth.JwtProvider;
import com.jarvis.global.auth.RefreshCookieManager;
import com.jarvis.global.auth.TokenEpoch;
import com.jarvis.global.config.SecurityConfig;
import com.jarvis.member.Role;
import com.jarvis.order.OrderService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 03 D4 — /internal/**은 서비스 토큰 없이는 컨트롤러에 닿지 못한다.
 *
 * <p>필터 단독 테스트({@link InternalTokenFilterTest})로는 잡히지 않는 구멍을 막는다: 필터가 보는 경로와
 * 스프링이 컨트롤러를 고르는 경로가 다르게 해석되면 필터만 건너뛰고 컨트롤러에는 닿는다. 그래서 필터·시큐리티·
 * 라우팅을 함께 띄워 "토큰 없이 서비스가 호출되지 않는다"를 고정한다.
 */
@WebMvcTest(InternalOrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class,
        EnvelopeAuthenticationEntryPoint.class, EnvelopeAccessDeniedHandler.class,
        RefreshCookieManager.class, GuestCookieManager.class, ClientIp.class,
        AccessCookieManager.class})
@TestPropertySource(properties = {
        "jwt.secret=test-hs256-secret-key-at-least-256-bits-long-0123456789",
        "jwt.access-token-minutes=30",
        "jwt.refresh-token-days=14",
        "app.client-ip.forwarded-depth=2",
        "app.internal.token=test-internal-token"
})
class InternalSecurityTest {

    private static final String TOKEN = "test-internal-token";

    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean OrderService orderService;
    @MockitoBean TokenEpoch tokenEpoch;

    @Test
    @DisplayName("올바른 서비스 토큰이면 컨트롤러에 닿는다")
    void validToken_reachesController() throws Exception {
        mockMvc.perform(get("/internal/members/1/orders").header(InternalTokenFilter.HEADER, TOKEN))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("토큰 없음 — 401 INTERNAL_TOKEN_INVALID, 서비스 미호출")
    void noToken_401() throws Exception {
        mockMvc.perform(get("/internal/members/1/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_TOKEN_INVALID"));
        verifyNoInteractions(orderService);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/%69nternal/members/1/orders",
            "/i%6Eternal/members/1/orders",
            "/%69%6E%74%65%72%6E%61%6C/members/1/orders",
            "/internal%2Fmembers/1/orders",
            "/internal;x=1/members/1/orders",
            "//internal/members/1/orders",
            "/./internal/members/1/orders",
            "/INTERNAL/members/1/orders"
    })
    @DisplayName("경로를 변형해도 토큰 없이는 서비스가 호출되지 않는다")
    void disguisedPath_neverReachesService(String rawPath) throws Exception {
        int status = mockMvc.perform(get(URI.create(rawPath))).andReturn().getResponse().getStatus();

        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(200);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("로그인한 일반 회원도 서비스 토큰 없이는 거부")
    void loggedInMember_withoutToken_rejected() throws Exception {
        Cookie access = new Cookie(AccessCookieManager.COOKIE_NAME, jwtProvider.createAccessToken(1L, Role.USER));

        mockMvc.perform(get("/internal/members/2/orders").cookie(access))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(URI.create("/%69nternal/members/2/orders")).cookie(access))
                .andExpect(result -> org.assertj.core.api.Assertions
                        .assertThat(result.getResponse().getStatus()).isNotEqualTo(200));
        verifyNoInteractions(orderService);
    }
}
