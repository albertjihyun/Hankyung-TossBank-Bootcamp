package com.jarvis.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.jarvis.global.redis.RedisCircuitBreaker;
import com.jarvis.global.ratelimit.PublicWriteRateLimiter.Scope;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 로그인 없이 쓰는 쓰기 API의 IP 단위 제한 (07 §3-4) */
@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class PublicWriteRateLimiterTest {

    private static final String IP = "203.0.113.9";

    @Mock StringRedisTemplate redisTemplate;

    /** 창 60초 / 이벤트 300회 / 채팅 60회 */
    private PublicWriteRateLimiter limiter() {
        return new PublicWriteRateLimiter(new FixedWindowCounter(redisTemplate, new RedisCircuitBreaker()), 60, 300, 60);
    }

    private void stub(String key, long count) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), anyString()))
                .thenReturn(List.of(count, 30_000L));
    }

    @Test
    @DisplayName("한도까지는 통과한다")
    void atLimitPasses() {
        stub("ratelimit:public:events:" + IP, 300);

        limiter().check(Scope.EVENTS, IP);
    }

    @Test
    @DisplayName("한도를 넘으면 429 — 쿠키 없는 요청을 쏟아부어 게스트 행·가짜 이벤트를 찍어내지 못한다")
    void overLimitIsRejected() {
        stub("ratelimit:public:events:" + IP, 301);

        assertThatThrownBy(() -> limiter().check(Scope.EVENTS, IP))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("범위마다 따로 센다 — 이벤트를 많이 보낸 IP도 채팅 한도는 따로다")
    void scopesAreCountedSeparately() {
        stub("ratelimit:public:chat:" + IP, 61);

        assertThatThrownBy(() -> limiter().check(Scope.CHAT, IP))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("Redis가 죽으면 통과시킨다 (fail-open) — 제한이 잠깐 없는 것이 수집 전면 중단보다 낫다")
    void redisDownFailsOpen() {
        when(redisTemplate.execute(any(RedisScript.class), any(List.class), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        limiter().check(Scope.EVENTS, IP);
    }
}
