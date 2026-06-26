package com.hackathon.olive.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JWT 블랙리스트. 스테이트리스 JWT의 약점(만료 전 강제 무효화 불가)을 보완.
 * 로그아웃 시 액세스 토큰의 jti를 등록 → 만료 시각까지 거부.
 */
@Entity
@Table(name = "blacklisted_tokens")
@Getter
@Setter
@NoArgsConstructor
public class BlacklistedToken {

    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public BlacklistedToken(String jti, Instant expiresAt) {
        this.jti = jti;
        this.expiresAt = expiresAt;
    }
}
