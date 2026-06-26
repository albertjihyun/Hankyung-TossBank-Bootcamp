package com.hackathon.olive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 리프레시 토큰. HttpSession(서버 세션)과 함께 'JWT 재발급 앵커' 역할.
 * 로그아웃/탈취 시 이 레코드를 지우면 더 이상 액세스 토큰을 재발급 못 함.
 */
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken {

    @Id
    @Column(length = 100)
    private String token; // UUID

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public RefreshToken(String token, Long userId, Instant expiresAt) {
        this.token = token;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
