package com.hackathon.openrun.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예약 = Member 와 Event 의 연결. (TECH_SPEC §2 reservation / §3)
 *
 * <p>(event_id, member_id) UNIQUE 로 "1인 1예약" — 중복 예약을 DB 제약으로 차단(2차 방어선).
 */
@Entity
@Table(
        name = "reservation",
        uniqueConstraints = @UniqueConstraint(name = "uk_resv_event_member", columnNames = {"event_id", "member_id"}),
        indexes = @Index(name = "idx_resv_event_status", columnList = "event_id, status, waiting_seq")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 뷰(내 예약/참가자 현황)가 event/member 를 참조하고 open-in-view=false 이므로 EAGER.
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id", nullable = false, foreignKey = @ForeignKey(name = "fk_resv_event"))
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_resv_member"))
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "waiting_seq")
    private Integer waitingSeq; // WAITING 일 때 대기 순번 (RESERVED 면 null)

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private Reservation(Event event, Member member, ReservationStatus status, Integer waitingSeq) {
        this.event = event;
        this.member = member;
        this.status = status;
        this.waitingSeq = waitingSeq;
        this.createdAt = LocalDateTime.now();
    }

    public static Reservation reserved(Event event, Member member) {
        return Reservation.builder()
                .event(event).member(member)
                .status(ReservationStatus.RESERVED)
                .waitingSeq(null)
                .build();
    }

    public static Reservation waiting(Event event, Member member, int seq) {
        return Reservation.builder()
                .event(event).member(member)
                .status(ReservationStatus.WAITING)
                .waitingSeq(seq)
                .build();
    }

    /* ===== 상태 변경 도메인 메서드 ===== */

    public void cancel() {
        this.status = ReservationStatus.CANCELLED;
        this.waitingSeq = null;
    }

    /** 대기 → 예약확정 승계. */
    public void promote() {
        this.status = ReservationStatus.RESERVED;
        this.waitingSeq = null;
    }

    public boolean isActive() {
        return status == ReservationStatus.RESERVED || status == ReservationStatus.WAITING;
    }
}
