package com.jarvis.global.redis;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

/**
 * Redis 회로 차단기 (07 §4-4, 2026-11-17).
 *
 * <p><b>무엇을 막나</b> — Redis를 쓰는 곳 대부분은 fail-open이다(캐시 미스로 DB, 토큰 무효화 미적용, 요청 제한
 * 없이 통과). 그런데 fail-open은 <b>실패를 확인한 뒤에야</b> 열린다. Redis가 응답하지 않으면 호출마다 커맨드
 * 타임아웃(2초)을 기다리고, 인증된 요청 하나가 토큰 무효화 조회·요청 제한·캐시를 거치며 이걸 여러 번 겪는다.
 * 장애 동안 모든 API가 수 초씩 느려지고 그만큼 톰캣 스레드와 DB 커넥션이 묶인다.
 *
 * <p><b>어떻게</b> — 연결 실패·타임아웃이 연속 {@value #FAILURE_THRESHOLD}번이면 회로를 열고
 * {@link #OPEN_DURATION} 동안 Redis를 부르지 않고 바로 대체값을 돌려준다. 그 뒤 한 요청만 시험 삼아 보내고,
 * 성공하면 닫고 실패하면 다시 연다.
 *
 * <p>Lua 오류·역직렬화 실패 같은 <b>장애가 아닌 오류는 세지 않는다</b> — 버그 하나가 Redis 전체를 끄면 안 된다.
 * 채팅 세션처럼 Redis 없이는 성립하지 않는 경로(fail-closed)는 여기를 거치지 않는다.
 */
@Slf4j
@Component
public class RedisCircuitBreaker {

    static final int FAILURE_THRESHOLD = 3;
    static final Duration OPEN_DURATION = Duration.ofSeconds(10);

    private final LongSupplier nanoClock;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicBoolean trialInFlight = new AtomicBoolean();
    private volatile long openUntilNanos;
    private volatile boolean open;

    public RedisCircuitBreaker() {
        this(System::nanoTime);
    }

    RedisCircuitBreaker(LongSupplier nanoClock) {
        this.nanoClock = nanoClock;
    }

    /**
     * {@code op}을 실행한다. 회로가 열려 있으면 실행하지 않고 {@code fallback}을 돌려준다. 실행 중 예외가 나면
     * {@code onError}로 알리고(호출자가 자기 문맥으로 로그를 남긴다) {@code fallback}을 돌려준다.
     */
    public <T> T call(Supplier<T> op, T fallback, Consumer<RuntimeException> onError) {
        boolean trial = false;
        if (open) {
            if (nanoClock.getAsLong() < openUntilNanos || !trialInFlight.compareAndSet(false, true)) {
                return fallback;
            }
            trial = true;
        }
        try {
            T result = op.get();
            onSuccess();
            return result;
        } catch (RuntimeException e) {
            onFailure(e);
            onError.accept(e);
            return fallback;
        } finally {
            if (trial) {
                trialInFlight.set(false);
            }
        }
    }

    public void run(Runnable op, Consumer<RuntimeException> onError) {
        call(() -> {
            op.run();
            return null;
        }, null, onError);
    }

    public boolean isOpen() {
        return open;
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        if (open) {
            open = false;
            log.info("Redis 회로 닫힘 — 호출 재개");
        }
    }

    private void onFailure(RuntimeException e) {
        if (!isOutage(e)) {
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (open || failures >= FAILURE_THRESHOLD) {
            openUntilNanos = nanoClock.getAsLong() + OPEN_DURATION.toNanos();
            if (!open) {
                open = true;
                log.warn("Redis 회로 열림 — 연속 {}회 연결 실패. {}초 동안 Redis 호출을 생략하고 대체값으로 응답한다",
                        failures, OPEN_DURATION.toSeconds());
            }
        }
    }

    /** 연결 실패와 커맨드 타임아웃만 장애로 본다 */
    static boolean isOutage(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RedisConnectionFailureException || t instanceof QueryTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
