package com.jarvis.global.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

class RedisCircuitBreakerTest {

    private final AtomicLong now = new AtomicLong();
    private final RedisCircuitBreaker breaker = new RedisCircuitBreaker(now::get);
    private final AtomicInteger calls = new AtomicInteger();

    private String failing() {
        calls.incrementAndGet();
        throw new RedisConnectionFailureException("down");
    }

    private String ok() {
        calls.incrementAndGet();
        return "ok";
    }

    private String call(boolean succeed) {
        return breaker.call(succeed ? this::ok : this::failing, "fallback", e -> { });
    }

    @Test
    @DisplayName("연속 3번 연결 실패하면 열리고, 열린 동안은 Redis를 부르지 않는다")
    void opensAfterThresholdAndSkipsCalls() {
        for (int i = 0; i < RedisCircuitBreaker.FAILURE_THRESHOLD; i++) {
            assertThat(call(false)).isEqualTo("fallback");
        }
        assertThat(breaker.isOpen()).isTrue();

        calls.set(0);
        assertThat(call(true)).isEqualTo("fallback");
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("열린 시간이 지나면 한 번 시험하고, 성공하면 닫힌다")
    void halfOpenTrialCloses() {
        for (int i = 0; i < RedisCircuitBreaker.FAILURE_THRESHOLD; i++) {
            call(false);
        }
        now.addAndGet(RedisCircuitBreaker.OPEN_DURATION.toNanos());

        assertThat(call(true)).isEqualTo("ok");
        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    @DisplayName("시험이 실패하면 다시 열린 시간만큼 쉰다")
    void failedTrialReopens() {
        for (int i = 0; i < RedisCircuitBreaker.FAILURE_THRESHOLD; i++) {
            call(false);
        }
        now.addAndGet(RedisCircuitBreaker.OPEN_DURATION.toNanos());
        call(false);

        calls.set(0);
        now.addAndGet(RedisCircuitBreaker.OPEN_DURATION.toNanos() - 1);
        assertThat(call(true)).isEqualTo("fallback");
        assertThat(calls).hasValue(0);
    }

    @Test
    @DisplayName("중간에 성공하면 실패 횟수가 초기화된다 — '연속' 실패만 센다")
    void successResetsCount() {
        call(false);
        call(false);
        call(true);
        call(false);
        call(false);

        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    @DisplayName("장애가 아닌 오류(Lua 오류 등)는 세지 않는다 — 버그 하나가 Redis 전체를 끄지 않는다")
    void nonOutageErrorsDoNotCount() {
        for (int i = 0; i < 10; i++) {
            breaker.call(() -> {
                throw new RedisSystemException("script error", new IllegalStateException());
            }, "fallback", e -> { });
        }

        assertThat(breaker.isOpen()).isFalse();
    }

    @Test
    @DisplayName("커맨드 타임아웃은 장애로 센다")
    void timeoutCounts() {
        assertThat(RedisCircuitBreaker.isOutage(new QueryTimeoutException("timeout"))).isTrue();
        assertThat(RedisCircuitBreaker.isOutage(new IllegalStateException())).isFalse();
    }
}
