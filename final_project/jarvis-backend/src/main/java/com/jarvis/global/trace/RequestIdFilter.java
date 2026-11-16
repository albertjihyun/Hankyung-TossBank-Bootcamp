package com.jarvis.global.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 요청 하나를 FE·Spring·AI 세 시스템 로그에서 잇는 키 (docs/tracing-headers.md §1).
 *
 * <p>들어온 {@code X-Request-Id}가 형식에 맞으면 그대로 쓰고, 없거나 형식이 다르면 새로 만든다.
 * 그 값을 ① 로그(MDC) ② 응답 헤더 ③ 오류 응답 본문({@code error.requestId}) ④ AI 서버로 나가는 호출
 * ({@link RequestIdPropagator})에 싣는다.
 *
 * <p>형식을 검증하는 이유는 로그 주입이다 — 클라이언트가 정하는 값을 그대로 로그에 찍으면 개행으로 로그
 * 한 줄을 여러 줄로 쪼개거나 긴 문자열로 로그를 부풀릴 수 있다. 형식이 틀려도 400을 내지 않고 조용히
 * 교체한다. 추적을 돕는 기능이 요청을 막으면 안 된다.
 *
 * <p>가장 먼저 돈다 — 인증 필터가 401을 쓸 때도 그 응답과 로그에 요청 ID가 있어야 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    /** 로그 패턴(application.yml {@code logging.pattern.correlation})이 읽는 MDC 키 */
    public static final String MDC_KEY = "requestId";

    private static final Pattern ACCEPTED = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String requestId = accept(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 톰캣은 스레드를 재사용한다 — 지우지 않으면 다음 요청의 로그에 이 요청의 ID가 찍힌다
            MDC.remove(MDC_KEY);
        }
    }

    /** 지금 처리 중인 요청의 ID. 요청 밖(스케줄러·컨슈머)에서는 null */
    public static String currentRequestId() {
        return MDC.get(MDC_KEY);
    }

    private static String accept(String incoming) {
        if (incoming != null && ACCEPTED.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
