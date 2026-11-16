package com.jarvis.order;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * mock 배송 전이 + 클레임 자동 승인 (01 §6, D4·D10) — 1분 틱.
 * 2층 방어: 조건부 UPDATE(정합성 최종 방어선) + Redis 분산 락(틱당 1대 — 03 D-분산5).
 * 같은 틱 안에서 연쇄 전이 없음 — 전이 시 status_changed_at이 NOW()로 갱신되므로 다음 단계 임계값에 미달.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMockScheduler {

    /**
     * 한 틱이 처리하는 클레임 상한. 남은 건은 다음 틱(1분 뒤)이 가져간다 — 장애 복구 직후처럼 밀린 건이
     * 많아도 한 틱이 락 보유 시간(50초)을 넘기지 않게 한다.
     */
    static final int CLAIM_BATCH_SIZE = 200;

    private final OrderStatusChanger statusChanger;
    private final MockProperties mockProperties;

    // lockAtLeastFor — 일이 금방 끝나도 락을 45초는 쥔다. 없으면 락은 "겹침"만 막고 "빈도"는 못 막아서,
    // 인스턴스 4대의 1분 틱이 서로 엇갈려 같은 잡이 분당 4번까지 돈다(03 D-분산5의 "틱당 1대"가 성립하지 않는다)
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "order-shipment-transition", lockAtMostFor = "PT50S", lockAtLeastFor = "PT45S")
    public void transitionShipment() {
        LocalDateTime now = LocalDateTime.now();
        // ORDERED→SHIPPING은 이 잡의 대상이 아니다 (2026-08-06, 01 D4 개정) — 판매자 발송(I-30)만이
        // 그 전이를 일으킨다. mock 자동 전이는 판매자 기능이 없어서 뒀던 대체물이라 진짜가 생긴 이상 걷어낸다.
        // 발송 주체가 없는 브랜드의 주문은 ORDERED에 머문다 — 필요하면 DB로 직접 밀어 쓴다.
        statusChanger.transitionShipment(OrderItemStatus.SHIPPING, OrderItemStatus.DELIVERED,
                now.minusMinutes(mockProperties.deliveryMinutes()), true);
        // CONFIRMED 전이는 로그 미기록 (01 §6.5 규칙 2). 반품 신청 중(*_REQUESTED)은 WHERE에서 자연 제외 (01 D8)
        statusChanger.transitionShipment(OrderItemStatus.DELIVERED, OrderItemStatus.CONFIRMED,
                now.minusMinutes(mockProperties.confirmMinutes()), false);
    }

    /**
     * 클레임을 <b>한 건씩 따로</b> 승인한다 — 건마다 독립 트랜잭션이다.
     *
     * <p>예전에는 대상 전부를 한 트랜잭션으로 처리했다. 그러면 한 건의 실패(재고 복원 중 락 타임아웃 등)가
     * 그 틱의 승인 전부를 롤백시키고, 같은 건이 다음 틱에도 맨 앞에서 다시 실패해 <b>모든 사용자의
     * 취소·반품이 무기한 처리 중</b>에 머문다. 지금은 실패한 건만 건너뛰고 나머지는 승인된다.
     */
    @Scheduled(fixedDelay = 60_000)
    @SchedulerLock(name = "order-claim-auto-approve", lockAtMostFor = "PT50S", lockAtLeastFor = "PT45S")
    public void approveClaims() {
        LocalDateTime now = LocalDateTime.now();
        List<Long> due = statusChanger.findDueClaimIds(
                now.minusMinutes(mockProperties.claimApproveMinutes()), CLAIM_BATCH_SIZE);
        int failed = 0;
        for (Long claimId : due) {
            try {
                statusChanger.approveClaim(claimId, now);
            } catch (RuntimeException e) {
                failed++;
                log.error("클레임 자동 승인 실패 — 이 건만 건너뛰고 계속한다 (claimId={})", claimId, e);
            }
        }
        if (failed > 0) {
            log.warn("클레임 자동 승인 — 대상 {}건 중 {}건 실패. 실패 건은 다음 틱에 다시 시도된다",
                    due.size(), failed);
        }
    }
}
