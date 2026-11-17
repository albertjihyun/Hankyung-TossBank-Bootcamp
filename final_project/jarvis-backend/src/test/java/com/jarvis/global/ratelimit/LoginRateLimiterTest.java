package com.jarvis.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.global.response.BusinessException;
import com.jarvis.global.response.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 로그인·가입 시도 제한 (07 §3-4) — IP·계정 두 축, 고정 창, fail-open */
@SuppressWarnings("unchecked") // RedisScript는 raw 타입이라 스텁·검증에서 캐스팅 경고가 불가피하다
@ExtendWith(MockitoExtension.class)
class LoginRateLimiterTest {

    private static final String IP = "203.0.113.9";
    private static final String EMAIL = "user@test.com";
    private static final String IP_KEY = "ratelimit:login:ip:" + IP;
    private static final String ACCT_KEY = "ratelimit:login:acct:" + EMAIL;

    @Mock StringRedisTemplate redisTemplate;

    /** 창 10분 / 계정 5회 / IP 30회 / 계정 전체 실패 50회 */
    private LoginRateLimiter limiter() {
        return new LoginRateLimiter(new FixedWindowCounter(redisTemplate), 10, 5, 30, 50);
    }

    private void stub(String key, long count, long ttlMillis) {
        when(redisTemplate.execute(any(RedisScript.class), eq(List.of(key)), anyString()))
                .thenReturn(List.of(count, ttlMillis));
    }

    @Test
    @DisplayName("한도 안이면 통과한다")
    void underLimitPasses() {
        stub(IP_KEY, 3, 600_000);
        stub(ACCT_KEY, 2, 600_000);

        assertThatCode(() -> limiter().check(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("계정 한도를 넘으면 429 — 같은 계정을 여러 IP에서 두드리는 걸 막는다")
    void accountLimitExceeded() {
        stub(IP_KEY, 6, 600_000);      // IP는 여유 있음
        stub(ACCT_KEY, 6, 600_000);    // 계정이 초과

        assertThatThrownBy(() -> limiter().check(IP, EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("IP 한도를 넘으면 429 — 한 IP에서 계정을 바꿔가며 두드리는 걸 막는다")
    void ipLimitExceeded() {
        stub(IP_KEY, 31, 300_000);
        stub(ACCT_KEY, 1, 300_000);

        assertThatThrownBy(() -> limiter().check(IP, EMAIL))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.RATE_LIMITED);
    }

    @Test
    @DisplayName("초과해도 두 축을 모두 센다 — 한쪽만 세면 다른 축이 비어 우회로가 생긴다")
    void countsBothAxesEvenWhenExceeded() {
        stub(IP_KEY, 31, 600_000);
        stub(ACCT_KEY, 1, 600_000);

        assertThatThrownBy(() -> limiter().check(IP, EMAIL))
                .isInstanceOf(BusinessException.class);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(IP_KEY)), anyString());
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(ACCT_KEY)), anyString());
    }

    @Test
    @DisplayName("Retry-After는 남은 TTL에서 계산한다 (두 축 중 긴 쪽)")
    void retryAfterFromLongerTtl() {
        stub(IP_KEY, 31, 90_000);      // 90초
        stub(ACCT_KEY, 1, 120_000);    // 120초

        assertThatThrownBy(() -> limiter().check(IP, EMAIL))
                .isInstanceOf(RateLimitedException.class)
                .extracting("retryAfterSeconds").isEqualTo(120L);
    }

    @Test
    @DisplayName("Redis가 죽으면 통과시킨다 (fail-open) — 방어 부재가 전원 로그인 불가보다 낫다")
    void redisDownFailsOpen() {
        when(redisTemplate.execute(any(RedisScript.class), any(), anyString()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThatCode(() -> limiter().check(IP, EMAIL)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("계정이 없으면 IP 축만 센다 (계정 키를 만들지 않는다)")
    void nullAccountCountsIpOnly() {
        stub(IP_KEY, 1, 600_000);

        assertThatCode(() -> limiter().check(IP, null)).doesNotThrowAnyException();

        verify(redisTemplate, never()).execute(any(RedisScript.class),
                eq(List.of(ACCT_KEY)), anyString());
    }

    @Test
    @DisplayName("계정 키는 소문자로 정규화한다 — 대소문자를 바꿔 한도를 우회하지 못하게")
    void accountKeyIsCaseInsensitive() {
        stub(IP_KEY, 1, 600_000);
        stub(ACCT_KEY, 1, 600_000);

        limiter().check(IP, "USER@TEST.COM");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(ACCT_KEY)), anyString());
    }

    @Test
    @DisplayName("한도와 같은 횟수까지는 통과 — 초과부터 막는다")
    void exactlyAtLimitPasses() {
        stub(IP_KEY, 30, 600_000);
        stub(ACCT_KEY, 5, 600_000);

        assertThatCode(() -> limiter().check(IP, EMAIL)).doesNotThrowAnyException();
    }

    // ---- 로그인 전용 (2026-11-17) — 계정 축은 실패만, (계정, IP) 쌍으로 센다 ----

    private static final String PAIR_KEY = "ratelimit:login:fail:" + EMAIL + ":" + IP;
    private static final String GLOBAL_KEY = "ratelimit:login:fail:" + EMAIL;

    @Mock org.springframework.data.redis.core.ValueOperations<String, String> valueOperations;

    private void stubFailures(String key, Long count) {
        org.mockito.Mockito.lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.lenient().when(valueOperations.get(key))
                .thenReturn(count == null ? null : String.valueOf(count));
        org.mockito.Mockito.lenient().when(redisTemplate.getExpire(eq(key), any(java.util.concurrent.TimeUnit.class)))
                .thenReturn(300_000L);
    }

    @Test
    @DisplayName("로그인 — 판정만으로는 계정 실패 횟수가 오르지 않는다(성공하는 로그인은 계정 한도를 깎지 않는다)")
    void loginCheckDoesNotCountAccountAxis() {
        stub(IP_KEY, 1, 600_000);
        stubFailures(PAIR_KEY, null);
        stubFailures(GLOBAL_KEY, null);

        limiter().checkLogin(IP, EMAIL);

        verify(redisTemplate, org.mockito.Mockito.never())
                .execute(any(RedisScript.class), eq(List.of(PAIR_KEY)), anyString());
        verify(redisTemplate, org.mockito.Mockito.never())
                .execute(any(RedisScript.class), eq(List.of(GLOBAL_KEY)), anyString());
    }

    @Test
    @DisplayName("로그인 — 같은 IP에서 같은 계정을 5번 틀렸으면 그 IP는 막힌다")
    void loginBlockedAfterPairFailures() {
        stub(IP_KEY, 6, 600_000);
        stubFailures(PAIR_KEY, 5L);
        stubFailures(GLOBAL_KEY, 5L);

        assertThatThrownBy(() -> limiter().checkLogin(IP, EMAIL)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("로그인 — 남이 다른 IP에서 내 계정을 틀려도 나는 막히지 않는다(계정 잠금 DoS 방지)")
    void victimIsNotLockedOutByAttackerFromAnotherIp() {
        // 공격자가 다른 IP에서 6번 틀려 계정 전체 실패가 6이지만, 내 IP의 실패는 0이다
        stub(IP_KEY, 1, 600_000);
        stubFailures(PAIR_KEY, null);
        stubFailures(GLOBAL_KEY, 6L);

        limiter().checkLogin(IP, EMAIL);
    }

    @Test
    @DisplayName("로그인 — 계정 전체 실패가 상한(50)에 닿으면 어느 IP에서든 막는다(IP를 바꿔가며 두드리는 분산 공격의 상한)")
    void loginBlockedWhenAccountUnderDistributedAttack() {
        stub(IP_KEY, 1, 600_000);
        stubFailures(PAIR_KEY, null);
        stubFailures(GLOBAL_KEY, 50L);

        assertThatThrownBy(() -> limiter().checkLogin(IP, EMAIL)).isInstanceOf(RateLimitedException.class);
    }

    @Test
    @DisplayName("로그인 실패 — (계정, IP) 쌍과 계정 전체 카운터를 둘 다 올린다")
    void loginFailureCountsBothAxes() {
        stub(PAIR_KEY, 1, 600_000);
        stub(GLOBAL_KEY, 1, 600_000);

        limiter().recordLoginFailure(IP, EMAIL);

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(PAIR_KEY)), anyString());
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(GLOBAL_KEY)), anyString());
    }

    @Test
    @DisplayName("로그인 성공 — 그 IP의 실패 기록만 지운다. 계정 전체 카운터는 공격 신호라 남긴다")
    void loginSuccessClearsOnlyPairCounter() {
        limiter().recordLoginSuccess(IP, EMAIL);

        verify(redisTemplate).delete(PAIR_KEY);
        verify(redisTemplate, org.mockito.Mockito.never()).delete(GLOBAL_KEY);
    }

    @Test
    @DisplayName("계정 키는 앞뒤 공백·대소문자를 지워 만든다 — 표기만 바꿔 다른 카운터로 세게 만들지 못한다")
    void accountKeyIsNormalized() {
        stub(PAIR_KEY, 1, 600_000);
        stub(GLOBAL_KEY, 1, 600_000);

        limiter().recordLoginFailure(IP, "  User@Test.COM ");

        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(PAIR_KEY)), anyString());
    }
}
