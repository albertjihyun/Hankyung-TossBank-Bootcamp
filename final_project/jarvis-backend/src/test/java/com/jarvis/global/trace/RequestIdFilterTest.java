package com.jarvis.global.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jarvis.global.response.ApiResponse;
import com.jarvis.global.response.ErrorCode;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** X-Request-Id 규약 (docs/tracing-headers.md §1) — 수용·검증·응답 echo·오류 본문·하위 호출 전파 */
class RequestIdFilterTest {

    private static final String VALID = "abcDEF0123456789_-xyz";

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private String run(String incoming, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products/1");
        if (incoming != null) {
            request.addHeader(RequestIdFilter.HEADER, incoming);
        }
        AtomicReference<String> seenInside = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> seenInside.set(RequestIdFilter.currentRequestId()));
        return seenInside.get();
    }

    @Test
    @DisplayName("형식에 맞는 값은 그대로 유지한다 — 한 값이 끝까지 살아남아야 로그가 이어진다")
    void keepsWellFormedIncomingId() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String inside = run(VALID, response);

        assertThat(inside).isEqualTo(VALID);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(VALID);
    }

    @Test
    @DisplayName("헤더가 없으면 새로 만들어 응답 헤더로 알려준다")
    void generatesWhenAbsent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String inside = run(null, response);

        assertThat(inside).matches("[A-Za-z0-9_-]{16,64}");
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(inside);
    }

    @ParameterizedTest(name = "[{index}]")
    @ValueSource(strings = {
            "short",
            "has space in the middle 0123456789",
            "line1-0123456789abcdef\nFAKE LOG LINE",
            "semi;colon-0123456789abcdef",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    @DisplayName("형식이 틀린 값은 버리고 새로 만든다 — 개행·긴 문자열로 로그를 오염시킬 수 없다. 400은 내지 않는다")
    void replacesMalformedIncomingId(String malformed) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        String inside = run(malformed, response);

        assertThat(inside).isNotEqualTo(malformed).matches("[A-Za-z0-9_-]{16,64}");
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("요청이 끝나면 지운다 — 스레드가 재사용돼도 다음 요청 로그에 남지 않는다")
    void clearsAfterRequest() throws Exception {
        run(VALID, new MockHttpServletResponse());

        assertThat(RequestIdFilter.currentRequestId()).isNull();
    }

    @Test
    @DisplayName("오류 응답 본문에 requestId가 실린다 — 사용자가 문의에 붙일 수 있다")
    void errorBodyCarriesRequestId() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, VALID);

        String json = new ObjectMapper().writeValueAsString(ApiResponse.error(ErrorCode.INTERNAL_ERROR));

        assertThat(json).contains("\"requestId\":\"" + VALID + "\"");
    }

    @Test
    @DisplayName("요청 밖에서 만든 오류 응답에는 requestId 키 자체가 없다")
    void errorBodyOmitsRequestIdOutsideRequest() throws Exception {
        String json = new ObjectMapper().writeValueAsString(ApiResponse.error(ErrorCode.INTERNAL_ERROR));

        assertThat(json).doesNotContain("requestId");
    }

    @Test
    @DisplayName("AI 서버로 나가는 호출에 같은 값을 싣는다")
    void propagatesToOutboundCall() throws Exception {
        MDC.put(RequestIdFilter.MDC_KEY, VALID);
        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.POST, "/events/session-end");

        new RequestIdPropagator().intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], 200));

        assertThat(outbound.getHeaders().getFirst(RequestIdFilter.HEADER)).isEqualTo(VALID);
    }

    @Test
    @DisplayName("요청 밖에서 나가는 호출에는 헤더를 붙이지 않는다 — 받는 쪽이 새로 만든다")
    void doesNotPropagateOutsideRequest() throws Exception {
        MockClientHttpRequest outbound = new MockClientHttpRequest(HttpMethod.POST, "/events/session-end");

        new RequestIdPropagator().intercept(outbound, new byte[0],
                (request, body) -> new MockClientHttpResponse(new byte[0], 200));

        assertThat(outbound.getHeaders().containsKey(RequestIdFilter.HEADER)).isFalse();
    }
}
