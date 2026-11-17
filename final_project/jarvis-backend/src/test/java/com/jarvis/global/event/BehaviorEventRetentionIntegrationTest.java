package com.jarvis.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jarvis.support.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BehaviorEventRetentionIntegrationTest extends MariaDbIntegrationTest {

    @Autowired BehaviorEventRepository repository;

    private void event(String createdAt) {
        jdbc.update("INSERT INTO behavior_events (session_key, client_event_id, event_type, occurred_at, created_at) "
                + "VALUES ('s', ?, 'page_view', " + createdAt + ", " + createdAt + ")", UUID.randomUUID().toString());
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM behavior_events", Long.class);
    }

    @Test
    @DisplayName("보존 기간이 지난 로그만 지우고 최근 로그는 남긴다")
    void purgesOnlyExpiredRows() {
        jdbc.update("DELETE FROM behavior_events");
        event("NOW() - INTERVAL 800 DAY");
        event("NOW() - INTERVAL 761 DAY");
        event("NOW() - INTERVAL 700 DAY");
        event("NOW()");
        BehaviorEventRetentionJob job = new BehaviorEventRetentionJob(repository, 760);

        int removed = job.purgeOlderThan(LocalDateTime.now().minusDays(760));

        assertThat(removed).isEqualTo(2);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    @DisplayName("배치 크기만큼만 지운다 — 한 번의 DELETE가 테이블 전체를 잡지 않는다")
    void deletesAtMostBatchSize() {
        jdbc.update("DELETE FROM behavior_events");
        for (int i = 0; i < 5; i++) {
            event("NOW() - INTERVAL 800 DAY");
        }

        int removed = repository.deleteOlderThan(LocalDateTime.now().minusDays(760), 3);

        assertThat(removed).isEqualTo(3);
        assertThat(count()).isEqualTo(2);
    }

    @Test
    @DisplayName("보존 기간이 분석 기간 상한(731일) 이하면 기동을 막는다")
    void rejectsRetentionShorterThanAnalysisWindow() {
        assertThatThrownBy(() -> new BehaviorEventRetentionJob(repository, 365))
                .isInstanceOf(IllegalStateException.class);
    }
}
