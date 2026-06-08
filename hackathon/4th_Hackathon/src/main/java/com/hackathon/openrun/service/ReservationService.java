package com.hackathon.openrun.service;

import com.hackathon.openrun.domain.Event;
import com.hackathon.openrun.domain.EventStatus;
import com.hackathon.openrun.domain.Member;
import com.hackathon.openrun.domain.Reservation;
import com.hackathon.openrun.domain.ReservationStatus;
import com.hackathon.openrun.exception.AlreadyReservedException;
import com.hackathon.openrun.exception.ForbiddenException;
import com.hackathon.openrun.exception.NotFoundException;
import com.hackathon.openrun.exception.NotOpenException;
import com.hackathon.openrun.repository.EventRepository;
import com.hackathon.openrun.repository.MemberRepository;
import com.hackathon.openrun.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시성 핵심 (TECH_SPEC §4). 한정 좌석에 대한 선착순 예약과 취소→대기 승계를
 * <b>DB 비관적 락(SELECT ... FOR UPDATE)</b> 으로 직렬화하여 정합성을 보장한다.
 *
 * <p>왜 in-app 락(synchronized)이 아니라 DB 락인가? (ADR-1) 인스턴스가 2대 이상이면
 * JVM 내 락은 무력하다. 정합성의 단일 진실은 공유 DB 한 곳뿐이므로 락도 DB에서 잡는다.
 */
@Service
public class ReservationService {

    private final EventRepository eventRepository;
    private final ReservationRepository reservationRepository;
    private final MemberRepository memberRepository;

    public ReservationService(EventRepository eventRepository,
                              ReservationRepository reservationRepository,
                              MemberRepository memberRepository) {
        this.eventRepository = eventRepository;
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
    }

    /**
     * 선착순 예약 (TECH_SPEC §4.1 — 비관적 락).
     * 정원 내면 RESERVED, 초과면 WAITING(대기 순번 부여).
     *
     * @return 확정된 예약 상태(RESERVED 또는 WAITING)
     */
    @Transactional
    public ReservationStatus reserve(Long eventId, Long memberId) {
        // (1) 행사 행에 비관적 쓰기 락 → 이 행에 대한 동시 트랜잭션을 직렬화
        Event event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 행사입니다."));

        // (2) 오픈 상태/시각 검증
        if (event.getStatus() == EventStatus.SCHEDULED || LocalDateTime.now().isBefore(event.getOpenAt())) {
            throw new NotOpenException("아직 오픈 전인 행사입니다.");
        }
        // 참고: 정원이 가득 차 CLOSED 인 행사여도 '대기 등록'은 허용한다(아래 좌석 판정 분기).
        // 취소로 좌석이 빈 경우는 decreaseReserved() 가 자동으로 OPEN 으로 되돌린다.

        // (3) 1인 1예약 — 앱 레벨 선검사 (DB UNIQUE 제약이 2차 방어선)
        if (reservationRepository.existsByEventIdAndMemberId(eventId, memberId)) {
            throw new AlreadyReservedException("이미 신청한 행사입니다.");
        }

        Member member = memberRepository.getReferenceById(memberId);

        // (4) 좌석 판정
        if (!event.isFull()) {
            reservationRepository.save(Reservation.reserved(event, member));
            event.increaseReserved(); // reserved_count++ (가득 차면 내부에서 CLOSED 전이)
            return ReservationStatus.RESERVED;
        } else {
            int seq = reservationRepository.countByEventIdAndStatus(eventId, ReservationStatus.WAITING) + 1;
            reservationRepository.save(Reservation.waiting(event, member, seq));
            return ReservationStatus.WAITING;
        }
    }

    /**
     * 예약/대기 취소 + 대기 승계 (TECH_SPEC §4.2).
     * RESERVED 취소로 좌석이 비면 대기열 선두를 자동 승계(WAITING→RESERVED).
     */
    @Transactional
    public void cancel(Long reservationId, Long memberId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 예약입니다."));

        if (!reservation.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("본인 예약만 취소할 수 있습니다.");
        }
        if (!reservation.isActive()) {
            throw new NotOpenException("이미 취소된 예약입니다.");
        }

        // 좌석 수 변경 → 행사 행에 비관적 락
        Event event = eventRepository.findByIdForUpdate(reservation.getEvent().getId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 행사입니다."));

        boolean wasReserved = (reservation.getStatus() == ReservationStatus.RESERVED);
        reservation.cancel();

        if (wasReserved) {
            event.decreaseReserved();
            // 대기열 선두를 승계
            reservationRepository
                    .findFirstByEventIdAndStatusOrderByWaitingSeqAsc(event.getId(), ReservationStatus.WAITING)
                    .ifPresent(next -> {
                        next.promote();           // WAITING → RESERVED
                        event.increaseReserved(); // 좌석 다시 채움
                    });
        }
    }

    /* ===== 조회 (읽기 전용) ===== */

    @Transactional(readOnly = true)
    public List<Reservation> findMyReservations(Long memberId) {
        return reservationRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public List<Reservation> findEventReservations(Long eventId) {
        return reservationRepository.findByEventIdOrderByStatusAscWaitingSeqAsc(eventId);
    }
}
