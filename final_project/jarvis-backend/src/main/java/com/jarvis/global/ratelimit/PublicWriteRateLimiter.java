package com.jarvis.global.ratelimit;

import com.jarvis.global.ratelimit.FixedWindowCounter.Attempt;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인 없이 쓸 수 있는 쓰기 API의 IP 단위 요청 제한 (07 §3-4).
 *
 * <p>행동 이벤트 수집(E-1)과 채팅 세션·티켓 발급(CH-1·CH-1b)은 인증 없이 호출된다. 제한이 없으면
 * 쿠키 없는 요청을 반복해 ① 요청마다 게스트 행을 만들고 ② 가짜 이벤트로 판매자 분석(조회수·퍼널·
 * 실시간 방문자)을 오염시키고 ③ Redis에 채팅 세션 키를 쌓을 수 있었다. 문서는 이걸 "nginx 몫"으로
 * 미뤘지만 그 설정은 들어가지 않았다.
 *
 * <p>한도는 넉넉하다 — 회사·카페처럼 여러 사람이 한 IP를 쓰는 곳을 막지 않으면서, 스크립트로 쏟아붓는
 * 것만 끊는 게 목적이다. IP는 위조 방어를 거친 값({@code ClientIp})을 받는다.
 */
@Slf4j
@Component
public class PublicWriteRateLimiter {

    private static final String KEY = "ratelimit:public:";

    /** 제한을 따로 세는 단위 */
    public enum Scope { EVENTS, CHAT }

    private final FixedWindowCounter counter;
    private final Duration window;
    private final int eventsLimit;
    private final int chatLimit;

    public PublicWriteRateLimiter(FixedWindowCounter counter,
                                  @Value("${app.rate-limit.public.window-seconds}") long windowSeconds,
                                  @Value("${app.rate-limit.public.events-ip-limit}") int eventsLimit,
                                  @Value("${app.rate-limit.public.chat-ip-limit}") int chatLimit) {
        this.counter = counter;
        this.window = Duration.ofSeconds(windowSeconds);
        this.eventsLimit = eventsLimit;
        this.chatLimit = chatLimit;
    }

    public void check(Scope scope, String clientIp) {
        Attempt attempt = counter.hit(KEY + scope.name().toLowerCase() + ":" + clientIp, window);
        if (attempt.count() <= limitOf(scope)) {
            return;
        }
        long retryAfterSeconds = Math.max(1, attempt.ttlMillis() / 1000);
        // 초과 직후 한 번만 남긴다 — 쏟아붓는 쪽이 로그까지 부풀리지 못하게
        if (attempt.count() == limitOf(scope) + 1) {
            log.warn("비인증 쓰기 요청 제한 — scope={} ip={} retryAfter={}s", scope, clientIp, retryAfterSeconds);
        }
        throw new RateLimitedException(retryAfterSeconds);
    }

    private int limitOf(Scope scope) {
        return scope == Scope.EVENTS ? eventsLimit : chatLimit;
    }
}
