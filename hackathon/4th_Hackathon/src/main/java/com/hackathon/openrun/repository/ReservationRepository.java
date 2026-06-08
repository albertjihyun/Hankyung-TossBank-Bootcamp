package com.hackathon.openrun.repository;

import com.hackathon.openrun.domain.Reservation;
import com.hackathon.openrun.domain.ReservationStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByEventIdAndMemberId(Long eventId, Long memberId);

    int countByEventIdAndStatus(Long eventId, ReservationStatus status);

    /** 대기열 선두(가장 작은 waiting_seq) — 취소 시 승계 대상. (TECH_SPEC §4.2) */
    Optional<Reservation> findFirstByEventIdAndStatusOrderByWaitingSeqAsc(Long eventId, ReservationStatus status);

    /** 내 예약/대기 현황 (최신순). */
    List<Reservation> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /** HOST 참가자 현황 / ADMIN 조회. */
    List<Reservation> findByEventIdOrderByStatusAscWaitingSeqAsc(Long eventId);

    Optional<Reservation> findByEventIdAndMemberId(Long eventId, Long memberId);
}
