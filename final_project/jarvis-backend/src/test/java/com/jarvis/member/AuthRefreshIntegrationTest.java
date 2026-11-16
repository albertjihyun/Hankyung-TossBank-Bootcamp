package com.jarvis.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jarvis.cart.CartService;
import com.jarvis.chat.ChatSessionService;
import com.jarvis.global.auth.JwtProperties;
import com.jarvis.global.auth.JwtProvider;
import com.jarvis.global.auth.TokenEpoch;
import com.jarvis.global.auth.TokenHasher;
import com.jarvis.global.ratelimit.LoginRateLimiter;
import com.jarvis.global.response.BusinessException;
import com.jarvis.member.dto.AuthResult;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 재발급(A-4)의 트랜잭션 동작을 실제 MariaDB에서 확인한다 (02 D50).
 *
 * <p>재사용이 탐지되면 패밀리를 폐기하고 <b>예외로</b> 끝난다. 예외는 기본적으로 트랜잭션을 롤백시키므로,
 * 설정이 어긋나면 "폐기했다"는 로그만 남고 실제로는 아무것도 지워지지 않는다. 목으로는 이걸 볼 수 없다.
 */
@Import(AuthService.class)
class AuthRefreshIntegrationTest extends MariaDbIntegrationTest {

    @Autowired AuthService authService;
    @Autowired RefreshTokenRepository refreshTokenRepository;

    @MockitoBean JwtProvider jwtProvider;
    @MockitoBean JwtProperties jwtProperties;
    @MockitoBean TokenEpoch tokenEpoch;
    @MockitoBean PasswordEncoder passwordEncoder;
    @MockitoBean AccountEventLogger accountEventLogger;
    @MockitoBean CartService cartService;
    @MockitoBean ChatSessionService chatSessionService;
    @MockitoBean LoginRateLimiter loginRateLimiter;

    private long memberId;

    @BeforeEach
    void setUp() {
        memberId = new TestData(jdbc).member();
        when(jwtProvider.createAccessToken(anyLong(), any())).thenReturn("access-token");
        when(jwtProperties.refreshTokenDays()).thenReturn(14L);
    }

    private void issue(String raw, String familyId) {
        refreshTokenRepository.saveAndFlush(RefreshToken.issue(memberId, TokenHasher.sha256Hex(raw),
                LocalDateTime.now().plusDays(14), familyId));
    }

    private int tokensInFamily(String familyId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM refresh_token WHERE family_id = ?", Integer.class, familyId);
    }

    @Test
    @DisplayName("정상 재발급 — 쓴 RT는 회전 표시로 남고, 새 RT가 같은 패밀리에 한 줄 더 생긴다")
    void refreshKeepsTheFamily() {
        issue("rt-1", "family-1");

        AuthResult result = authService.refresh("rt-1");

        assertThat(tokensInFamily("family-1")).isEqualTo(2);
        RefreshToken used = refreshTokenRepository.findByTokenHash(TokenHasher.sha256Hex("rt-1")).orElseThrow();
        assertThat(used.isRotated()).isTrue();
        RefreshToken fresh = refreshTokenRepository
                .findByTokenHash(TokenHasher.sha256Hex(result.refreshToken())).orElseThrow();
        assertThat(fresh.isRotated()).isFalse();
        assertThat(fresh.getFamilyId()).isEqualTo("family-1");
    }

    @Test
    @DisplayName("탈취 시나리오 — 옛 RT가 유예 밖에서 다시 오면 그 로그인의 RT가 실제로 전부 지워진다(예외로 끝나도 커밋된다)")
    void reuseAfterGraceReallyDeletesTheFamily() {
        issue("rt-1", "family-1");
        String rotated = authService.refresh("rt-1").refreshToken();   // 정상 사용자가 rt-1 → rt-2로 회전
        // 유예(10초)가 지난 것으로 만든다
        jdbc.update("UPDATE refresh_token SET rotated_at = NOW() - INTERVAL 1 MINUTE WHERE token_hash = ?",
                TokenHasher.sha256Hex("rt-1"));
        issue("other-device", "family-2");

        // 공격자가 훔쳐 둔 rt-1을 쓴다
        assertThatThrownBy(() -> authService.refresh("rt-1")).isInstanceOf(BusinessException.class);

        assertThat(tokensInFamily("family-1")).as("rt-1과 rt-2 모두 폐기").isZero();
        assertThat(tokensInFamily("family-2")).as("다른 기기의 로그인은 그대로").isEqualTo(1);
        verify(tokenEpoch).invalidate(memberId);
        // 정상 사용자의 새 RT도 죽었다 — 양쪽 다 재로그인해야 하고, 비밀번호를 모르는 쪽은 거기서 끊긴다
        assertThatThrownBy(() -> authService.refresh(rotated)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("탭 경합 — 회전 직후 같은 RT가 한 번 더 와도 발급하고, 패밀리는 살아 있다")
    void immediateSecondUseIsServed() {
        issue("rt-1", "family-1");
        authService.refresh("rt-1");

        AuthResult second = authService.refresh("rt-1");

        assertThat(second.refreshToken()).isNotBlank();
        assertThat(tokensInFamily("family-1")).isEqualTo(3);
    }

    @Test
    @DisplayName("로그아웃 — 그 로그인의 RT 전부가 지워져, 유예로 받은 형제 RT로도 재발급할 수 없다")
    void logoutRemovesWholeFamily() {
        issue("rt-1", "family-1");
        String sibling = authService.refresh("rt-1").refreshToken();
        String another = authService.refresh("rt-1").refreshToken();

        authService.logout(another, "1.2.3.4");

        assertThat(tokensInFamily("family-1")).isZero();
        assertThatThrownBy(() -> authService.refresh(sibling)).isInstanceOf(BusinessException.class);
    }
}
