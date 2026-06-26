package com.hackathon.olive.repository;

import com.hackathon.olive.domain.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface BlacklistedTokenRepository extends JpaRepository<BlacklistedToken, String> {
    boolean existsByJti(String jti);

    @Modifying
    @Query("delete from BlacklistedToken b where b.expiresAt < :now")
    int deleteExpired(Instant now);
}
