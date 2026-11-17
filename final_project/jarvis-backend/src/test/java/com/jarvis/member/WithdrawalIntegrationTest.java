package com.jarvis.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jarvis.address.AddressRepository;
import com.jarvis.cart.CartItemRepository;
import com.jarvis.chat.ChatSessionService;
import com.jarvis.global.auth.TokenEpoch;
import com.jarvis.global.response.BusinessException;
import com.jarvis.global.response.ErrorCode;
import com.jarvis.order.OrderItemRepository;
import com.jarvis.profile.ProfileService;
import com.jarvis.support.MariaDbIntegrationTest;
import com.jarvis.support.TestData;
import com.jarvis.wishlist.WishlistRepository;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** M-17 탈퇴를 실제 MariaDB에서 — 무엇이 지워지고 무엇이 남는지 (02 D55) */
class WithdrawalIntegrationTest extends MariaDbIntegrationTest {

    @Autowired MemberRepository memberRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired CartItemRepository cartItemRepository;
    @Autowired WishlistRepository wishlistRepository;
    @Autowired OrderItemRepository orderItemRepository;
    @Autowired AccountEventLogRepository accountEventLogRepository;
    @Autowired PlatformTransactionManager txManager;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final ProfileService profileService = mock(ProfileService.class);
    private WithdrawalService service;
    private TestData data;
    private long memberId;
    private long productId;

    @BeforeEach
    void setUp() {
        service = new WithdrawalService(memberRepository, refreshTokenRepository, addressRepository,
                cartItemRepository, wishlistRepository, orderItemRepository,
                new AccountEventLogger(accountEventLogRepository), encoder,
                mock(TokenEpoch.class), mock(ChatSessionService.class), profileService);
        data = new TestData(jdbc);
        memberId = data.member();
        productId = data.product();
        jdbc.update("UPDATE member SET password = ?, birth_date = '1995-07-15' WHERE id = ?",
                encoder.encode("secret"), memberId);
        jdbc.update("INSERT INTO address (member_id, label, recipient, phone, zip_code, address1, is_default, "
                + "created_at) VALUES (?, '집', '홍길동', '010', '12345', '서울', TRUE, NOW())", memberId);
        jdbc.update("INSERT INTO cart_item (member_id, product_id, quantity, created_at) VALUES (?, ?, 1, NOW())",
                memberId, productId);
        jdbc.update("INSERT INTO wishlist (member_id, product_id, created_at) VALUES (?, ?, NOW())",
                memberId, productId);
        jdbc.update("INSERT INTO refresh_token (member_id, token_hash, family_id, expires_at, created_at) "
                + "VALUES (?, REPEAT('a', 64), UUID(), NOW() + INTERVAL 1 DAY, NOW())", memberId);
    }

    private void withdraw(String password) {
        new TransactionTemplate(txManager).executeWithoutResult(s -> service.withdraw(memberId, password, "1.2.3.4"));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE member_id = ?", Long.class, memberId);
    }

    @Test
    @DisplayName("본인 데이터는 지우고, 회원 행은 알아볼 수 없게 바꾸고, 주문은 남긴다")
    void wipesPersonalDataAndKeepsOrders() {
        long orderId = data.order(memberId, "PAID");
        data.orderItem(orderId, productId, "CONFIRMED");

        withdraw("secret");

        assertThat(count("address")).isZero();
        assertThat(count("cart_item")).isZero();
        assertThat(count("wishlist")).isZero();
        assertThat(count("refresh_token")).isZero();
        assertThat(count("orders")).isEqualTo(1);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT email, nickname, birth_date, withdrawn_at FROM member WHERE id = ?", memberId);
        assertThat(row.get("email").toString()).isEqualTo("withdrawn-" + memberId + "@deleted.invalid");
        assertThat(row.get("nickname")).isEqualTo("탈퇴회원");
        assertThat(row.get("birth_date").toString()).isEqualTo("1995-01-01");
        assertThat(row.get("withdrawn_at")).isNotNull();
        // 옛 비밀번호로는 더 이상 맞지 않는다
        String hash = jdbc.queryForObject("SELECT password FROM member WHERE id = ?", String.class, memberId);
        assertThat(encoder.matches("secret", hash)).isFalse();
    }

    @Test
    @DisplayName("원래 이메일이 비워져 같은 주소로 다시 가입할 수 있다")
    void originalEmailIsFreed() {
        String email = jdbc.queryForObject("SELECT email FROM member WHERE id = ?", String.class, memberId);

        withdraw("secret");

        assertThat(memberRepository.existsByEmail(email)).isFalse();
    }

    @Test
    @DisplayName("배송 중인 주문이 있으면 409 — 아무것도 지우지 않는다")
    void blockedByShippingOrder() {
        data.orderItem(data.order(memberId, "PAID"), productId, "SHIPPING");

        assertThatThrownBy(() -> withdraw("secret"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_HAS_ACTIVE_ORDERS);
        assertThat(count("address")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM member WHERE id = ?", Object.class, memberId))
                .isNull();
    }

    @Test
    @DisplayName("결제 전 주문은 막지 않는다 — 돈이 오가지 않았다")
    void pendingOrderDoesNotBlock() {
        data.orderItem(data.order(memberId, "PENDING"), productId, "PENDING");

        withdraw("secret");

        assertThat(count("address")).isZero();
    }

    @Test
    @DisplayName("비밀번호가 틀리면 400 — 아무것도 지우지 않고 AI 쪽도 건드리지 않는다")
    void wrongPassword() {
        assertThatThrownBy(() -> withdraw("wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.MEMBER_PASSWORD_MISMATCH);
        assertThat(count("cart_item")).isEqualTo(1);
        verify(profileService, never()).purgeForWithdrawal(anyLong());
    }

    @Test
    @DisplayName("AI 취향 삭제는 커밋 뒤에 부르고, 실패해도 탈퇴는 그대로 남는다")
    void aiPurgeFailureDoesNotUndoWithdrawal() {
        doThrow(new IllegalStateException("AI down")).when(profileService).purgeForWithdrawal(memberId);

        withdraw("secret");

        verify(profileService).purgeForWithdrawal(memberId);
        assertThat(jdbc.queryForObject("SELECT withdrawn_at FROM member WHERE id = ?", Object.class, memberId))
                .isNotNull();
    }
}
