package com.jarvis.global.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jarvis.support.MariaDbIntegrationTest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 행동 이벤트의 DB 직접 적재(08 D7 — Kafka 발행 실패 시의 폴백)를 실제 MariaDB로 확인한다.
 *
 * <p>담기·결제 이벤트는 본 트랜잭션이 커밋된 뒤({@code afterCommit}) 발행하고, 발행이 실패하면 같은
 * 자리에서 이 적재기로 폴백한다. 그 자리는 "커밋은 끝났지만 트랜잭션 자원은 아직 스레드에 붙어 있는"
 * 특수한 구간이라, 여기서 평범하게 저장하면 이미 끝난 트랜잭션에 올라타 <b>아무도 커밋하지 않는다</b>.
 * 목 테스트로는 볼 수 없는 종류의 결함이다.
 */
@Import(BehaviorEventAppender.class)
class BehaviorEventAppenderIntegrationTest extends MariaDbIntegrationTest {

    @Autowired BehaviorEventAppender appender;
    @Autowired TransactionTemplate transactionTemplate;

    private static BehaviorEvent event(String clientEventId) {
        LocalDateTime now = LocalDateTime.now();
        return BehaviorEvent.record(null, null, "session-key", clientEventId, "purchase_complete",
                null, "{\"orderId\":1,\"amount\":12000}", now, EventAttribution.NONE, now);
    }

    private int storedEvents() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM behavior_events", Integer.class);
    }

    @Test
    @DisplayName("트랜잭션 밖에서 적재 — 저장된다 (E-1 수집·Kafka 컨슈머 경로)")
    void appendsOutsideTransaction() {
        appender.append(List.of(event(UUID.randomUUID().toString())));

        assertThat(storedEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("커밋 후 콜백에서 적재 — 저장된다. 브로커 장애 중의 결제·담기 이벤트가 사라지지 않는다 (08 D7)")
    void appendsFromAfterCommitCallback() {
        String clientEventId = UUID.randomUUID().toString();

        // OrderEventRecorder·CartEventRecorder가 하는 일을 그대로 재현한다:
        // 주문 트랜잭션 안에서 "커밋되면 이벤트를 적재하라"를 등록하고, 발행 실패로 폴백에 들어온 상황
        transactionTemplate.executeWithoutResult(status ->
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        appender.append(List.of(event(clientEventId)));
                    }
                }));

        assertThat(storedEvents())
                .as("커밋 후 콜백에서 폴백 적재한 이벤트가 DB에 남아 있어야 한다")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("본 트랜잭션이 롤백되면 콜백 자체가 돌지 않는다 — 실패한 주문의 이벤트는 남지 않는다")
    void nothingStoredWhenOuterTransactionRollsBack() {
        transactionTemplate.executeWithoutResult(status -> {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    appender.append(List.of(event(UUID.randomUUID().toString())));
                }
            });
            status.setRollbackOnly();
        });

        assertThat(storedEvents()).isZero();
    }

    @Test
    @DisplayName("중복이 아닌 제약 위반은 삼키지 않는다 — 문제 없는 이벤트는 저장하고 실패를 호출자에게 던진다")
    void nonDuplicateViolationIsNotSwallowed() {
        LocalDateTime now = LocalDateTime.now();
        // 존재하지 않는 게스트를 가리키는 이벤트 — fk_behavior_guest 위반이지 중복이 아니다
        BehaviorEvent orphan = BehaviorEvent.record(null, "3f2b8c1e-9a4d-4e6f-8b7a-1c2d3e4f5a6b",
                "session-key", UUID.randomUUID().toString(), "product_view", null, "{}", now,
                EventAttribution.NONE, now);
        BehaviorEvent healthy = event(UUID.randomUUID().toString());

        assertThatThrownBy(() -> appender.append(List.of(healthy, orphan)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(storedEvents()).as("문제 없는 이벤트는 함께 버려지지 않는다").isEqualTo(1);
    }

    @Test
    @DisplayName("같은 이벤트가 다시 오면 한 번만 저장한다 — 폴백과 Kafka 재전달이 겹쳐도 중복되지 않는다")
    void duplicateIsStoredOnce() {
        String clientEventId = UUID.randomUUID().toString();

        appender.append(List.of(event(clientEventId)));
        appender.append(List.of(event(clientEventId)));

        assertThat(storedEvents()).isEqualTo(1);
    }

    @Test
    @DisplayName("배치 안에 이미 있는 이벤트가 섞여도 새 이벤트는 전부 저장한다")
    void batchWithKnownDuplicateKeepsTheRest() {
        String known = UUID.randomUUID().toString();
        appender.append(List.of(event(known)));

        appender.append(List.of(event(known), event(UUID.randomUUID().toString()),
                event(UUID.randomUUID().toString())));

        assertThat(storedEvents()).isEqualTo(3);
    }
}
