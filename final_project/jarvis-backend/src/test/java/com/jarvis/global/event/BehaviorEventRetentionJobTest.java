package com.jarvis.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BehaviorEventRetentionJobTest {

    private final BehaviorEventRepository repository = mock(BehaviorEventRepository.class);
    private final BehaviorEventRetentionJob job = new BehaviorEventRetentionJob(repository, 760);

    @Test
    @DisplayName("배치가 가득 차면 이어서 지우고, 덜 차면 멈춘다")
    void loopsUntilPartialBatch() {
        when(repository.deleteOlderThan(any(), anyInt()))
                .thenReturn(BehaviorEventRetentionJob.BATCH_SIZE, BehaviorEventRetentionJob.BATCH_SIZE, 10);

        int total = job.purgeOlderThan(LocalDateTime.now());

        assertThat(total).isEqualTo(BehaviorEventRetentionJob.BATCH_SIZE * 2 + 10);
        verify(repository, times(3)).deleteOlderThan(any(), anyInt());
    }

    @Test
    @DisplayName("밀린 양이 많아도 회차 상한에서 멈춘다 — 새벽 작업이 낮까지 이어지지 않는다")
    void stopsAtRunLimit() {
        when(repository.deleteOlderThan(any(), anyInt())).thenReturn(BehaviorEventRetentionJob.BATCH_SIZE);

        job.purgeOlderThan(LocalDateTime.now());

        verify(repository, times(BehaviorEventRetentionJob.MAX_BATCHES_PER_RUN)).deleteOlderThan(any(), anyInt());
    }
}
