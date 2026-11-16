package com.jarvis.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * RT는 원문이 아니라 SHA-256 해시로 저장 (02 D17). 로그아웃/재발급 시 row 삭제·교체 (02 D6).
 */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64,
            columnDefinition = "CHAR(64)")
    private String tokenHash;

    /**
     * 한 번의 로그인에서 이어진 RT들의 묶음 (02 D50). 회전으로 새 RT를 내도 같은 값을 물려받는다 —
     * 로그아웃과 재사용 탐지가 이 값으로 그 로그인의 RT 전부를 한 번에 폐기한다.
     */
    @Column(name = "family_id", nullable = false, columnDefinition = "CHAR(36)")
    private String familyId;

    /** 이 RT로 재발급한 시각. 회전된 RT는 지우지 않고 이 값을 채워 남긴다 — 다시 오면 재사용임을 알 수 있다 */
    @Column(name = "rotated_at")
    private LocalDateTime rotatedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 로그인·가입 — 새 패밀리를 연다 */
    public static RefreshToken issue(Long memberId, String tokenHash, LocalDateTime expiresAt) {
        return issue(memberId, tokenHash, expiresAt, UUID.randomUUID().toString());
    }

    /** 회전 — 직전 RT의 패밀리를 물려받는다 */
    public static RefreshToken issue(Long memberId, String tokenHash, LocalDateTime expiresAt,
                                     String familyId) {
        RefreshToken token = new RefreshToken();
        token.memberId = memberId;
        token.tokenHash = tokenHash;
        token.expiresAt = expiresAt;
        token.familyId = familyId;
        return token;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    public boolean isExpired(LocalDateTime now) {
        return expiresAt.isBefore(now);
    }
}
