package com.jarvis.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.jarvis.support.Concurrently;
import com.jarvis.support.Concurrently.Outcome;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 리프레시 토큰 회전·패밀리 폐기·만료 정리를 실제 MariaDB에서 확인한다 (02 D6·D50).
 * "회전은 한 요청만 이긴다"는 조건부 UPDATE에 달려 있어, 목으로는 증명되지 않는다.
 */
class RefreshTokenIntegrationTest extends MariaDbIntegrationTest {

    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired TransactionTemplate transactionTemplate;

    private static String hash(String seed) {
        return com.jarvis.global.auth.TokenHasher.sha256Hex(seed);
    }

    private RefreshToken save(long memberId, String seed, String familyId, LocalDateTime expiresAt) {
        return refreshTokenRepository.saveAndFlush(RefreshToken.issue(memberId, hash(seed), expiresAt, familyId));
    }

    private int markRotated(String seed) {
        return transactionTemplate.execute(status ->
                refreshTokenRepository.markRotated(hash(seed), LocalDateTime.now()));
    }

    private int tokensOf(long memberId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE member_id = ?", Integer.class, memberId);
    }

    @Test
    @DisplayName("같은 RT로 6개 요청이 동시에 회전을 시도 — 한 요청만 이긴다")
    void rotationHasSingleWinner() throws Exception {
        long memberId = new TestData(jdbc).member();
        save(memberId, "rt-1", "family-1", LocalDateTime.now().plusDays(14));

        List<Callable<Integer>> requests = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            requests.add(() -> markRotated("rt-1"));
        }
        List<Outcome<Integer>> outcomes = Concurrently.run(requests);

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes.stream().mapToInt(Outcome::value).sum()).isEqualTo(1);
    }

    @Test
    @DisplayName("회전된 RT는 지워지지 않고 남는다 — 다시 오면 '이미 쓰인 RT'임을 알 수 있다")
    void rotatedTokenIsKeptAsEvidence() {
        long memberId = new TestData(jdbc).member();
        save(memberId, "rt-1", "family-1", LocalDateTime.now().plusDays(14));

        assertThat(markRotated("rt-1")).isEqualTo(1);
        assertThat(markRotated("rt-1")).as("두 번째 회전 시도는 0건").isZero();

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash("rt-1")).orElseThrow();
        assertThat(stored.isRotated()).isTrue();
    }

    @Test
    @DisplayName("패밀리 폐기 — 그 로그인에서 이어진 RT만 전부 지우고, 같은 회원의 다른 로그인은 남긴다")
    void revokingFamilyLeavesOtherLoginsAlone() {
        long memberId = new TestData(jdbc).member();
        save(memberId, "phone-1", "family-phone", LocalDateTime.now().plusDays(14));
        save(memberId, "phone-2", "family-phone", LocalDateTime.now().plusDays(14));
        save(memberId, "laptop-1", "family-laptop", LocalDateTime.now().plusDays(14));

        int removed = transactionTemplate.execute(status -> refreshTokenRepository.deleteByFamilyId("family-phone"));

        assertThat(removed).isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash(hash("laptop-1"))).isPresent();
        assertThat(tokensOf(memberId)).isEqualTo(1);
    }

    @Test
    @DisplayName("만료 정리 — 수명이 끝난 행만 지운다(회전 여부와 무관)")
    void cleanupRemovesOnlyExpiredRows() {
        long memberId = new TestData(jdbc).member();
        save(memberId, "expired-unused", "family-a", LocalDateTime.now().minusDays(1));
        save(memberId, "expired-rotated", "family-b", LocalDateTime.now().minusHours(1));
        save(memberId, "alive", "family-c", LocalDateTime.now().plusDays(1));
        markRotated("expired-rotated");

        int removed = transactionTemplate.execute(status ->
                refreshTokenRepository.deleteExpiredBefore(LocalDateTime.now()));

        assertThat(removed).isEqualTo(2);
        assertThat(refreshTokenRepository.findByTokenHash(hash("alive"))).isPresent();
    }
}
