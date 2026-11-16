package com.jarvis.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.global.response.ApiResponse;
import com.jarvis.global.response.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * /internal/** 서비스 토큰 검증 (03 D4) — FE 경유로는 절대 호출 불가 보장의 애플리케이션 층.
 * (네트워크 층 2겹 — nginx 미라우팅·포트 미노출 — 은 배포 형상 소관, 03 §1-1)
 * 토큰 미설정(빈 값)이면 전부 거부(fail-closed).
 *
 * <p>방어는 두 겹이다. ① 이 필터가 디코딩한 경로로 /internal/ 여부를 판정해 토큰을 검증하고,
 * ② 통과한 요청에만 {@link #ROLE} 권한을 심어 시큐리티가 {@code /internal/**}에 그 권한을 요구한다
 * (SecurityConfig). ②가 필요한 이유: 필터의 경로 판정과 스프링의 라우팅이 한 번이라도 다르게 해석되면
 * 필터만 건너뛰고 컨트롤러에는 닿는다 — 실제로 원본 URI({@code getRequestURI()}) 문자열 비교를 쓰던
 * 시절 {@code /%69nternal/...}이 토큰 없이 통과했다. 인가를 라우팅과 같은 매처에 맡기면 이 종류의
 * 불일치가 구조적으로 생기지 않는다.
 */
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";
    /** 서비스 토큰을 통과한 요청에만 부여 — 사용자 JWT로는 얻을 수 없는 권한이다 */
    public static final String ROLE = "INTERNAL";

    private static final String PATH_PREFIX = "/internal/";
    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final String internalToken;
    private final ObjectMapper objectMapper;

    public InternalTokenFilter(@Value("${app.internal.token:}") String internalToken,
                               ObjectMapper objectMapper) {
        this.internalToken = internalToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 원본 URI가 아니라 디코딩·정규화된 경로로 본다 — 스프링이 컨트롤러를 고를 때 쓰는 해석과 맞춘다
        return !PATH_HELPER.getPathWithinApplication(request).startsWith(PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (internalToken == null || internalToken.isBlank() || provided == null
                || !MessageDigest.isEqual(internalToken.getBytes(StandardCharsets.UTF_8),
                        provided.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            objectMapper.writeValue(response.getWriter(),
                    ApiResponse.error(ErrorCode.INTERNAL_TOKEN_INVALID));
            return;
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "internal-service", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE))));
        filterChain.doFilter(request, response);
    }
}
