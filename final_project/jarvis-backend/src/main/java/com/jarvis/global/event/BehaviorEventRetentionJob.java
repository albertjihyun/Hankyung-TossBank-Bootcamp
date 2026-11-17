package com.jarvis.global.event;

import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 행동 로그 보존 기간 정리 (02 D53).
 *
 * <p>behavior_events는 페이지 조회마다 쌓이는 가장 빠르게 자라는 테이블인데, 지우는 경로가 없었다.
 * 보존 기간은 판매자 분석 기간 상한({@code AnalysisPeriod.MAX_DAYS}=731일)보다 길어야 한다 —
 * 짧으면 "2년 분석"이 앞쪽 몇 주를 조용히 0으로 보여준다.
 *
 * <p>한 번에 지우지 않는다. 배치마다 따로 커밋해 잠금과 언두 로그를 짧게 끊고, 한 회차 상한을 둬서
 * 밀린 양이 많아도 새벽 작업이 낮까지 이어지지 않게 한다(남은 건 다음 날 이어서 지운다).
 */
@Slf4j
@Component
public class BehaviorEventRetentionJob {

    static final int BATCH_SIZE = 5_000;
    static final int MAX_BATCHES_PER_RUN = 200;

    private final BehaviorEventRepository repository;
    private final int retentionDays;

    public BehaviorEventRetentionJob(BehaviorEventRepository repository,
                                     @Value("${app.behavior-events.retention-days:760}") int retentionDays) {
        if (retentionDays <= 731) {
            // 분석 기간 상한보다 짧은 보존은 설정 실수다 — 조용히 데이터를 잃기 전에 기동을 막는다
            throw new IllegalStateException("app.behavior-events.retention-days는 731보다 커야 한다: " + retentionDays);
        }
        this.repository = repository;
        this.retentionDays = retentionDays;
    }

    @Scheduled(cron = "0 23 4 * * *")
    @SchedulerLock(name = "behavior-events-retention", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purge() {
        int total = purgeOlderThan(LocalDateTime.now().minusDays(retentionDays));
        if (total > 0) {
            log.info("보존 기간({}일)이 지난 행동 로그 {}건 정리", retentionDays, total);
        }
    }

    int purgeOlderThan(LocalDateTime cutoff) {
        int total = 0;
        for (int i = 0; i < MAX_BATCHES_PER_RUN; i++) {
            int removed = repository.deleteOlderThan(cutoff, BATCH_SIZE);
            total += removed;
            if (removed < BATCH_SIZE) {
                return total;
            }
        }
        log.warn("행동 로그 정리가 회차 상한({}건)에 닿았다 — 남은 건 다음 회차에 이어서 지운다",
                BATCH_SIZE * MAX_BATCHES_PER_RUN);
        return total;
    }
}
