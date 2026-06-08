package com.hackathon.openrun.repository;

import com.hackathon.openrun.domain.Category;
import com.hackathon.openrun.domain.Event;
import com.hackathon.openrun.domain.EventStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * 비관적 쓰기 락으로 행사 행을 조회 → {@code SELECT ... FOR UPDATE}.
     * 선착순 예약/취소 승계의 동시성 직렬화 지점. (TECH_SPEC §4.1 / §8.2 2단계)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    List<Event> findAllByOrderByOpenAtAsc();

    List<Event> findByStatusOrderByOpenAtAsc(EventStatus status);

    List<Event> findByCategoryOrderByOpenAtAsc(Category category);

    List<Event> findByStatusAndCategoryOrderByOpenAtAsc(EventStatus status, Category category);

    List<Event> findByHostIdOrderByCreatedAtDesc(Long hostId);

    /** 스케줄러: open_at 도래했지만 아직 SCHEDULED 인 행사들. (TECH_SPEC §3 상태 전이) */
    List<Event> findByStatusAndOpenAtLessThanEqual(EventStatus status, LocalDateTime now);
}
