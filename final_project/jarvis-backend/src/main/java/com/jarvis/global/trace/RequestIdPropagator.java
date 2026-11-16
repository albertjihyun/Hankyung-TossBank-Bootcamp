package com.jarvis.global.trace;

import java.io.IOException;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.lang.NonNull;

/**
 * AI 서버로 나가는 호출에 현재 요청의 {@code X-Request-Id}를 실어 보낸다 (docs/tracing-headers.md §1 「전파」).
 * 중간에서 새로 만들면 그 지점 앞뒤가 끊기므로, 받은 값을 그대로 넘기는 것이 전부다.
 * 요청 밖에서 나가는 호출(값이 없음)은 헤더를 붙이지 않는다 — 받는 쪽이 새로 만든다.
 */
public class RequestIdPropagator implements ClientHttpRequestInterceptor {

    @Override
    @NonNull
    public ClientHttpResponse intercept(@NonNull HttpRequest request, @NonNull byte[] body,
                                        @NonNull ClientHttpRequestExecution execution) throws IOException {
        String requestId = RequestIdFilter.currentRequestId();
        if (requestId != null && !request.getHeaders().containsKey(RequestIdFilter.HEADER)) {
            request.getHeaders().set(RequestIdFilter.HEADER, requestId);
        }
        return execution.execute(request, body);
    }
}
