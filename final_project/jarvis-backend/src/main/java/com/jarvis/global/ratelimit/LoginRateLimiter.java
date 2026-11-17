package com.jarvis.global.ratelimit;

import com.jarvis.global.ratelimit.FixedWindowCounter.Attempt;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로그인·회원가입 시도 제한 (07 §3-4) — 목적은 정밀 과금이 아니라 <b>무차별 대입 차단</b>이다.
 *
 * <p><b>IP와 계정 두 축을 같이 센다.</b> 한 축만 세면 각각 구멍이 있다 —
 * IP만 세면 공격자가 IP를 바꿔가며 분산하면 뚫리고, 계정만 세면 공격자가 남의 계정에 일부러
 * 실패를 쌓아 <b>잠가버리는 DoS</b>가 된다.
 *
 * <p><b>로그인의 계정 축은 "실패"만, "그 IP에서" 센다</b>(2026-11-17 개정). 예전에는 계정 축이 성공까지
 * 세고 IP를 가리지 않아서 ① 정상 사용자가 10분에 6번 로그인하면 막혔고 ② 누구든 남의 이메일로 6번만
 * 틀리면 그 사람이 10분간 로그인하지 못했다 — 피하겠다던 잠금 DoS 그대로였다. 지금은:
 * <ul>
 *   <li>(계정, IP) 쌍의 실패 — 좁은 한도. 공격자의 IP만 막히고 피해자는 자기 IP에서 로그인된다.
 *       성공하면 지운다.</li>
 *   <li>계정 전체의 실패 — 넓은 한도. IP를 바꿔가며 한 계정을 두드리는 분산 공격의 상한이다. 이 한도에
 *       닿으면 피해자도 잠깐 막히지만, 그건 실제로 대규모 공격을 받는 중이라는 뜻이다.</li>
 * </ul>
 * 가입은 성공·실패 구분이 없어 종전대로 시도를 전부 센다({@link #check}).
 *
 * <p>고정 창·원자적 집계·fail-open은 {@link FixedWindowCounter} 소관이다.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private static final String IP_KEY = "ratelimit:login:ip:";
    private static final String ACCOUNT_KEY = "ratelimit:login:acct:";
    private static final String FAILURE_KEY = "ratelimit:login:fail:";

    private final FixedWindowCounter counter;
    private final Duration window;
    private final int accountLimit;
    private final int ipLimit;
    private final int accountGlobalLimit;

    public LoginRateLimiter(FixedWindowCounter counter,
                            @Value("${app.rate-limit.login.window-minutes}") long windowMinutes,
                            @Value("${app.rate-limit.login.account-limit}") int accountLimit,
                            @Value("${app.rate-limit.login.ip-limit}") int ipLimit,
                            @Value("${app.rate-limit.login.account-global-limit}") int accountGlobalLimit) {
        this.counter = counter;
        this.window = Duration.ofMinutes(windowMinutes);
        this.accountLimit = accountLimit;
        this.ipLimit = ipLimit;
        this.accountGlobalLimit = accountGlobalLimit;
    }

    /**
     * 시도를 한 번 세고, 어느 축이든 한도를 넘었으면 429로 막는다.
     *
     * <p>초과를 발견해도 <b>두 축을 모두 센다</b> — 한쪽에서 조기 반환하면 다른 축의 카운트가
     * 누락돼, 공격자가 한 축만 채워두고 다른 축을 자유롭게 쓸 수 있다.
     */
    public void check(String clientIp, String account) {
        Attempt ip = counter.hit(IP_KEY + clientIp, window);
        Attempt acct = account == null
                ? Attempt.SKIPPED
                : counter.hit(ACCOUNT_KEY + normalize(account), window);

        boolean exceeded = ip.count() > ipLimit || acct.count() > accountLimit;
        if (!exceeded) {
            return;
        }
        long retryAfterSeconds = Math.max(1, Math.max(ip.ttlMillis(), acct.ttlMillis()) / 1000);
        log.warn("로그인 시도 제한 — ip={} ipCount={} acctCount={} retryAfter={}s",
                clientIp, ip.count(), acct.count(), retryAfterSeconds);
        throw new RateLimitedException(retryAfterSeconds);
    }

    /**
     * 로그인 시도 전 판정 — IP 축은 이 시도를 세고, 계정 축은 <b>세지 않고 읽기만</b> 한다.
     * 계정 축의 횟수는 실패했을 때 {@link #recordLoginFailure}가 올린다.
     */
    public void checkLogin(String clientIp, String account) {
        Attempt ip = counter.hit(IP_KEY + clientIp, window);
        Attempt pair = account == null ? Attempt.SKIPPED : counter.peek(pairKey(account, clientIp));
        Attempt global = account == null ? Attempt.SKIPPED : counter.peek(globalKey(account));
        boolean exceeded = ip.count() > ipLimit
                || pair.count() >= accountLimit
                || global.count() >= accountGlobalLimit;
        if (!exceeded) {
            return;
        }
        long retryAfterSeconds = Math.max(1,
                Math.max(ip.ttlMillis(), Math.max(pair.ttlMillis(), global.ttlMillis())) / 1000);
        log.warn("로그인 시도 제한 — ip={} ipCount={} pairFailures={} accountFailures={} retryAfter={}s",
                clientIp, ip.count(), pair.count(), global.count(), retryAfterSeconds);
        throw new RateLimitedException(retryAfterSeconds);
    }

    /** 로그인 실패 — 계정 축 두 카운터를 올린다(없는 계정으로 찌른 것도 실패다) */
    public void recordLoginFailure(String clientIp, String account) {
        if (account == null) {
            return;
        }
        counter.hit(pairKey(account, clientIp), window);
        counter.hit(globalKey(account), window);
    }

    /**
     * 로그인 성공 — 이 IP에서 쌓인 실패를 지운다. 계정 전체 카운터는 지우지 않는다 — 그 값은
     * "이 계정이 지금 여러 곳에서 공격받고 있는가"의 신호라, 주인이 로그인에 성공했다고 사라지면 안 된다.
     */
    public void recordLoginSuccess(String clientIp, String account) {
        if (account != null) {
            counter.reset(pairKey(account, clientIp));
        }
    }

    private static String pairKey(String account, String clientIp) {
        return FAILURE_KEY + normalize(account) + ":" + clientIp;
    }

    private static String globalKey(String account) {
        return FAILURE_KEY + normalize(account);
    }

    /** 앞뒤 공백과 대소문자를 지운다 — 같은 계정을 표기만 바꿔 다른 카운터로 세게 만들지 못하도록 */
    private static String normalize(String account) {
        return account.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
