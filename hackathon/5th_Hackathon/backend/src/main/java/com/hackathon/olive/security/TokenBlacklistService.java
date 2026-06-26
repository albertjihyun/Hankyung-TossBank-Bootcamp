package com.hackathon.olive.security;

import com.hackathon.olive.domain.BlacklistedToken;
import com.hackathon.olive.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final BlacklistedTokenRepository repository;

    @Transactional
    public void blacklist(String jti, Instant expiresAt) {
        if (jti != null && !repository.existsById(jti)) {
            repository.save(new BlacklistedToken(jti, expiresAt));
        }
    }

    public boolean isBlacklisted(String jti) {
        return jti != null && repository.existsByJti(jti);
    }

    /** 만료된 블랙리스트 항목은 더 거부할 필요 없으므로 주기적으로 정리(테이블 비대화 방지). */
    @Scheduled(fixedRate = 3_600_000) // 1시간
    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(Instant.now());
    }
}
