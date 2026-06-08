package com.hackathon.openrun.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사 = 한정 자원(좌석)의 컨테이너. 선착순 경합의 대상. (TECH_SPEC §2 event / §3)
 *
 * <p>동시성 핵심: {@code reserved_count} 직접 증감 + {@code @Version}(낙관적 락) 보유.
 * 비관적 락(SELECT ... FOR UPDATE)은 Repository.findByIdForUpdate 로 획득한다.
 * 상태 변경은 의미 있는 메서드(increaseReserved/decreaseReserved/open/close)로만 수행 — Setter 금지.
 */
@Entity
@Table(name = "event", indexes = @Index(name = "idx_event_status_open", columnList = "status, open_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // open-in-view=false 환경에서 뷰가 host.nickname 을 참조하므로 EAGER(=@ManyToOne 기본값) 유지.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "host_id", nullable = false, foreignKey = @jakarta.persistence.ForeignKey(name = "fk_event_host"))
    private Member host;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category;

    @Column(nullable = false)
    private int capacity; // 정원

    @Column(name = "reserved_count", nullable = false)
    private int reservedCount; // 현재 확정 예약 수 — 경합 지점

    @Column(name = "open_at", nullable = false)
    private LocalDateTime openAt; // 오픈 시각

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EventStatus status;

    @Version
    @Column(nullable = false)
    private Long version; // 낙관적 락 (README 실험용 비교군)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Event(Member host, String title, String description, Category category,
                  int capacity, LocalDateTime openAt, EventStatus status) {
        this.host = host;
        this.title = title;
        this.description = description;
        this.category = category;
        this.capacity = capacity;
        this.reservedCount = 0;
        this.openAt = openAt;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    /* ===== 상태 변경 도메인 메서드 ===== */

    public boolean isFull() {
        return reservedCount >= capacity;
    }

    public int getRemaining() {
        return Math.max(0, capacity - reservedCount);
    }

    public void increaseReserved() {
        this.reservedCount++;
        if (isFull()) {
            this.status = EventStatus.CLOSED;
        }
    }

    public void decreaseReserved() {
        if (this.reservedCount > 0) {
            this.reservedCount--;
        }
        // 좌석이 다시 생겼으므로(승계 대상) 오픈 상태로 되돌릴 수 있게 한다.
        if (this.status == EventStatus.CLOSED && !isFull()) {
            this.status = EventStatus.OPEN;
        }
    }

    public void open() {
        this.status = EventStatus.OPEN;
    }

    public void close() {
        this.status = EventStatus.CLOSED;
    }

    /** open_at 이 도래했고 아직 SCHEDULED 면 OPEN 으로 전이. */
    public boolean openIfDue(LocalDateTime now) {
        if (this.status == EventStatus.SCHEDULED && !now.isBefore(this.openAt)) {
            this.status = EventStatus.OPEN;
            return true;
        }
        return false;
    }
}
