package com.jarvis.global.event;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * behavior_events DB 적재 (04 §8). 컨슈머 A(정상 경로)와 발행 실패 시 폴백(08 D7)이 함께 쓴다.
 * 중복은 INSERT 전 검증 후 무시 — INSERT IGNORE는 중복 외 오류까지 삼키므로 금지 (02 D35).
 * 사전 검증을 통과한 뒤 경합으로 UNIQUE에 걸리면(같은 배치 동시 재전송) 배치 저장이 통째로
 * 롤백되므로, 그때만 건별 저장으로 내려가 충돌한 건만 버린다 — 정상 이벤트 동반 유실 방지.
 *
 * <p><b>실패를 삼키지 않는다</b>(2026-08-10, 08 D7) — 종전에는 {@code @Async} + warn 로그로
 * 유실을 감수했으나, 이제 적재 실패는 호출자가 판단한다: 폴백까지 실패한 E-1은 500(계약대로),
 * 컨슈머는 재시도 후 DLT. 조용한 유실이 관측 가능한 실패가 된 것이 이 변경의 핵심이다.
 */
@Slf4j
@Component
public class BehaviorEventAppender {

    private final BehaviorEventRepository behaviorEventRepository;
    private final TransactionTemplate transactionTemplate;

    /**
     * 적재는 <b>항상 새 트랜잭션</b>으로 한다(REQUIRES_NEW). 담기·결제 이벤트의 폴백은 본 트랜잭션의
     * {@code afterCommit} 콜백에서 불리는데, 그 시점은 커밋은 끝났지만 트랜잭션 자원이 아직 스레드에
     * 붙어 있다. 기본 전파(REQUIRED)로 저장하면 이미 끝난 트랜잭션에 올라타 <b>아무도 커밋하지 않고</b>
     * 예외도 없이 이벤트가 사라진다 — 브로커 장애 중의 결제·담기 이벤트가 전부 이 경로로 유실됐다
     * (2026-11-16 실제 DB로 재현, {@code BehaviorEventAppenderIntegrationTest}).
     *
     * <p>대가: 폴백이 도는 순간 그 요청은 커넥션을 둘 쥔다(본 트랜잭션의 것 + 이 적재의 것).
     * 폴백은 브로커 장애 중에만 돌고 차단기가 발행 대기를 없애 구간이 짧지만, 풀(인스턴스당 7)이 작아
     * 같은 순간 담기·결제가 몰리면 두 번째 커넥션을 기다리다 타임아웃(5s)이 날 수 있다. 그 경우는
     * 호출자(Recorder)가 잡아 "1건 유실"로 남긴다 — 결제는 이미 성공했다.
     */
    public BehaviorEventAppender(BehaviorEventRepository behaviorEventRepository,
                                 PlatformTransactionManager transactionManager) {
        this.behaviorEventRepository = behaviorEventRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void append(List<BehaviorEvent> events) {
        List<BehaviorEvent> fresh = dropKnownDuplicates(events);
        if (fresh.isEmpty()) {
            return;
        }
        try {
            transactionTemplate.executeWithoutResult(s -> behaviorEventRepository.saveAll(fresh));
        } catch (DataIntegrityViolationException e) {
            saveIndividually(fresh);
        }
    }

    private List<BehaviorEvent> dropKnownDuplicates(List<BehaviorEvent> events) {
        List<String> clientEventIds = events.stream()
                .map(BehaviorEvent::getClientEventId)
                .filter(Objects::nonNull)
                .toList();
        Set<String> existing = clientEventIds.isEmpty() ? Set.of()
                : new HashSet<>(behaviorEventRepository.findExistingClientEventIds(clientEventIds));
        List<BehaviorEvent> fresh = events.stream()
                .filter(e -> e.getClientEventId() == null || !existing.contains(e.getClientEventId()))
                .toList();
        if (fresh.size() < events.size()) {
            log.info("behavior_events 중복 {}건 무시 (D35)", events.size() - fresh.size());
        }
        return fresh;
    }

    /**
     * 배치가 제약 위반으로 깨진 경우 — 건별 트랜잭션으로 다시 저장해 문제 있는 건만 가려낸다.
     *
     * <p>제약 위반이 전부 중복은 아니다. 그 이벤트가 정말 이미 저장돼 있을 때만 중복으로 보고 버린다 —
     * 예전에는 FK·NOT NULL 위반까지 "중복 폐기"로 세어 조용히 버렸는데, 그건 이 클래스가 금지한
     * INSERT IGNORE와 같은 함정이다(02 D35). 중복이 아닌 실패는 나머지를 다 저장한 뒤 던져서
     * 호출자가 보게 한다(E-1은 500, 컨슈머는 재시도 후 DLT).
     */
    private void saveIndividually(List<BehaviorEvent> fresh) {
        int duplicates = 0;
        int failures = 0;
        DataIntegrityViolationException firstFailure = null;
        for (BehaviorEvent event : fresh) {
            try {
                // 깨진 배치가 일부 엔티티에 id를 채워 놓았으므로 원본이 아니라 사본을 넣는다(unsavedCopy 참조)
                BehaviorEvent retry = event.unsavedCopy();
                transactionTemplate.executeWithoutResult(s -> behaviorEventRepository.save(retry));
            } catch (DataIntegrityViolationException e) {
                if (alreadyStored(event)) {
                    duplicates++;
                    continue;
                }
                failures++;
                if (firstFailure == null) {
                    firstFailure = e;
                }
                log.warn("behavior_events 적재 실패 — 중복이 아닌 제약 위반 (clientEventId={}, eventType={})",
                        event.getClientEventId(), event.getEventType(), e);
            }
        }
        log.info("behavior_events 배치 경합 — 건별 저장으로 {}건 중 중복 {}건 폐기, 실패 {}건",
                fresh.size(), duplicates, failures);
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private boolean alreadyStored(BehaviorEvent event) {
        return event.getClientEventId() != null
                && !behaviorEventRepository.findExistingClientEventIds(List.of(event.getClientEventId())).isEmpty();
    }
}
