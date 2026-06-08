package com.hackathon.openrun.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hackathon.openrun.domain.Category;
import com.hackathon.openrun.domain.Event;
import com.hackathon.openrun.domain.EventStatus;
import com.hackathon.openrun.domain.Member;
import com.hackathon.openrun.domain.ReservationStatus;
import com.hackathon.openrun.domain.Role;
import com.hackathon.openrun.repository.EventRepository;
import com.hackathon.openrun.repository.MemberRepository;
import com.hackathon.openrun.repository.ReservationRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * 동시성 통합 테스트 (TECH_SPEC §11 / 기획서 §8).
 *
 * <p>정원 100 행사에 동시 예약 N건을 던졌을 때:
 * <ul>
 *   <li><b>2단계(비관적 락, 운영 코드)</b>: 정확히 capacity 만큼 RESERVED, 나머지 WAITING → oversell 0</li>
 *   <li><b>0단계(락 없음, 대비군)</b>: oversell 발생 → 락의 필요성 입증</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationConcurrencyTest {

    private static final int CAPACITY = 100;
    private static final int CONCURRENT_USERS = 300;

    @Autowired private ReservationService reservationService;
    @Autowired private NaiveReserver naiveReserver;
    @Autowired private EventRepository eventRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private ReservationRepository reservationRepository;

    @TestConfiguration
    static class TestBeans {
        @Bean
        NaiveReserver naiveReserver(JdbcTemplate jdbc) {
            return new NaiveReserver(jdbc);
        }
    }

    /**
     * 대비군: 0단계 "무방비" 예약 (TECH_SPEC §8.2 0단계 / §8.4 "0단계 일부러 재현").
     *
     * <p>핵심: Event 엔티티에는 {@code @Version}(낙관적 락)이 있어 JPA 경로로 카운터를 올리면
     * 낙관적 락이 자동으로 oversell 을 막는다. 진짜 '무방비'를 재현하려면 그 버전 체크까지
     * 우회해야 하므로 <b>네이티브 SQL(JdbcTemplate)</b> 로 read-check-write 를 수행한다.
     * read 와 write 사이에 간격을 둬 경합 창을 넓히면 다수 스레드가 동일한 낮은 카운트로
     * 통과해 정원을 초과하는 RESERVED 행이 생성된다.
     */
    static class NaiveReserver {
        private final JdbcTemplate jdbc;

        NaiveReserver(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void reserveNoLock(Long eventId, Long memberId) {
            Integer count = jdbc.queryForObject(
                    "SELECT reserved_count FROM event WHERE id = ?", Integer.class, eventId);
            Integer capacity = jdbc.queryForObject(
                    "SELECT capacity FROM event WHERE id = ?", Integer.class, eventId);
            if (count != null && capacity != null && count < capacity) {
                try {
                    Thread.sleep(15); // 경합 창 확대 — 여러 스레드가 동일 시점 read 값으로 통과
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                // 버전 체크 없는 네이티브 INSERT/UPDATE → 낙관적 락 우회
                jdbc.update("INSERT INTO reservation (event_id, member_id, status, created_at) VALUES (?,?,?,?)",
                        eventId, memberId, ReservationStatus.RESERVED.name(), Timestamp.valueOf(LocalDateTime.now()));
                jdbc.update("UPDATE event SET reserved_count = ? WHERE id = ?", count + 1, eventId);
            }
        }
    }

    @Test
    @DisplayName("비관적 락(운영): 동시 예약 300건 → 정확히 100 RESERVED, oversell 0")
    void pessimisticLock_noOversell() throws InterruptedException {
        Long eventId = setUp();

        Result r = fireConcurrently(memberId -> reservationService.reserve(eventId, memberId));

        Event event = eventRepository.findById(eventId).orElseThrow();
        long reservedRows = countByStatus(eventId, ReservationStatus.RESERVED);
        long waitingRows = countByStatus(eventId, ReservationStatus.WAITING);

        // 핵심 단언: 좌석 카운터·RESERVED 행이 정확히 정원과 일치 (oversell 0)
        assertThat(event.getReservedCount()).isEqualTo(CAPACITY);
        assertThat(reservedRows).isEqualTo(CAPACITY);
        assertThat(waitingRows).isEqualTo(CONCURRENT_USERS - CAPACITY);
        assertThat(r.success.get()).isEqualTo(CONCURRENT_USERS); // 전부 예약 또는 대기로 정상 처리
        assertThat(event.getStatus()).isEqualTo(EventStatus.CLOSED);
    }

    @Test
    @DisplayName("락 없음(대비군): 동시 예약 → oversell 발생으로 락의 필요성 입증")
    void noLock_oversells() throws InterruptedException {
        Long eventId = setUp();

        fireConcurrently(memberId -> naiveReserver.reserveNoLock(eventId, memberId));

        long reservedRows = countByStatus(eventId, ReservationStatus.RESERVED);
        // 락이 없으면 capacity(100)를 초과해 예약이 생성된다 → 정합성 붕괴
        assertThat(reservedRows).isGreaterThan(CAPACITY);
    }

    /* ===== 헬퍼 ===== */

    private final List<Long> userIds = new ArrayList<>();

    // 각 repository 호출이 독립 트랜잭션으로 커밋되어 동시 스레드에 보인다(의도적으로 비-@Transactional).
    Long setUp() {
        // 깨끗한 상태에서 시작
        reservationRepository.deleteAll();
        eventRepository.deleteAll();
        memberRepository.deleteAll();
        userIds.clear();

        Member host = memberRepository.save(Member.builder()
                .username("host").password("x").nickname("호스트").role(Role.HOST).build());
        for (int i = 0; i < CONCURRENT_USERS; i++) {
            Member u = memberRepository.save(Member.builder()
                    .username("u" + i).password("x").nickname("user" + i).role(Role.USER).build());
            userIds.add(u.getId());
        }
        Event event = eventRepository.save(Event.builder()
                .host(host).title("테스트 행사").description("동시성")
                .category(Category.POPUP).capacity(CAPACITY)
                .openAt(LocalDateTime.now().minusMinutes(1)).status(EventStatus.OPEN).build());
        return event.getId();
    }

    private interface ReserveCall {
        void call(Long memberId);
    }

    private Result fireConcurrently(ReserveCall call) throws InterruptedException {
        // 풀 크기 = 동시 사용자 수: 300개 태스크가 모두 동시에 출발 배리어에 도달해야
        // ready 가 0이 된다(풀이 작으면 일부만 실행돼 배리어에서 영구 대기 → 데드락).
        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_USERS);
        CountDownLatch ready = new CountDownLatch(CONCURRENT_USERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONCURRENT_USERS);
        Result result = new Result();

        for (Long memberId : userIds) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();               // 모든 스레드 동시 출발 → 경합 극대화
                    call.call(memberId);
                    result.success.incrementAndGet();
                } catch (Exception e) {
                    result.failure.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        ready.await(30, TimeUnit.SECONDS);   // 안전 타임아웃(무한 대기 방지)
        start.countDown();
        done.await(120, TimeUnit.SECONDS);
        pool.shutdownNow();
        return result;
    }

    private long countByStatus(Long eventId, ReservationStatus status) {
        return reservationRepository.findByEventIdOrderByStatusAscWaitingSeqAsc(eventId).stream()
                .filter(r -> r.getStatus() == status)
                .count();
    }

    private static class Result {
        final AtomicInteger success = new AtomicInteger();
        final AtomicInteger failure = new AtomicInteger();
    }
}
