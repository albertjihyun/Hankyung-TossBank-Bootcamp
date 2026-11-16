package com.jarvis.member;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * 회전 표시 — 아직 쓰이지 않은 RT일 때만 1건이 갱신된다 (02 D6·D50).
     * 같은 RT로 동시에 온 재발급은 DB가 직렬화해 한쪽만 1을 받는다. 0을 받은 쪽은 "이미 쓰인 RT"다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken r SET r.rotatedAt = :now WHERE r.tokenHash = :tokenHash AND r.rotatedAt IS NULL")
    int markRotated(@Param("tokenHash") String tokenHash, @Param("now") LocalDateTime now);

    /** 그 로그인의 RT 전부 폐기 — 로그아웃, 그리고 재사용(탈취) 탐지 시 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM RefreshToken r WHERE r.familyId = :familyId")
    int deleteByFamilyId(@Param("familyId") String familyId);

    /** 만료 행 정리 — 회전된 RT를 남기므로 주기적으로 걷어내지 않으면 쌓이기만 한다 */
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") LocalDateTime threshold);
}
