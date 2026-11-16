package com.jarvis.member;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 만료된 리프레시 토큰 정리 (02 D50).
 *
 * <p>회전된 RT를 지우지 않고 남기는 대신(재사용 탐지), 수명이 끝난 행은 주기적으로 걷어내야 한다.
 * 예전에는 로그아웃·회전 때만 지워서, 브라우저를 그냥 닫은 사용자의 RT는 만료된 채 영원히 남았다.
 * 만료된 RT는 어차피 재발급에 쓸 수 없으므로 지워도 동작이 달라지지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleaner {

    private final RefreshTokenRepository refreshTokenRepository;

    // 한 시간에 한 번이면 충분하다 — 급한 정리가 아니고, 인스턴스 여러 대가 같이 돌 이유도 없다
    @Scheduled(cron = "0 17 * * * *")
    @SchedulerLock(name = "refresh-token-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1M")
    @Transactional
    public void removeExpired() {
        int removed = refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("만료된 리프레시 토큰 {}건 정리", removed);
        }
    }
}
