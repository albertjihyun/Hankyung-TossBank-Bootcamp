package com.jarvis.global.event;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BehaviorEventRepository extends JpaRepository<BehaviorEvent, Long> {

    /** INSERT 전 중복 검증용 — INSERT IGNORE 금지 (02 D35) */
    @Query("select be.clientEventId from BehaviorEvent be where be.clientEventId in :ids")
    List<String> findExistingClientEventIds(@Param("ids") Collection<String> ids);
}
