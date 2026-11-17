package com.jarvis.member;

import com.jarvis.address.AddressRepository;
import com.jarvis.cart.CartItemRepository;
import com.jarvis.chat.ChatIdentity;
import com.jarvis.chat.ChatSessionService;
import com.jarvis.chat.SessionEndReason;
import com.jarvis.global.auth.TokenEpoch;
import com.jarvis.global.response.BusinessException;
import com.jarvis.global.response.ErrorCode;
import com.jarvis.order.OrderItemRepository;
import com.jarvis.profile.ProfileService;
import com.jarvis.wishlist.WishlistRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 회원 탈퇴 M-17 (02 D55).
 *
 * <p><b>지우는 것</b> — 본인만 쓰던 데이터: 장바구니·찜·배송지·리프레시 토큰. 회원 행의 이메일·닉네임·생일(월일).
 * <br><b>남기는 것</b> — 거래 기록(주문·클레임)은 전자상거래법상 보존 대상이고, 리뷰는 다른 구매자가 보는
 * 공용 콘텐츠라 작성자만 "탈퇴회원"으로 바뀐 채 남는다. 행동 로그는 회원 id로만 이어지며 보존 기간(D53)이
 * 지나면 지워진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AddressRepository addressRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistRepository wishlistRepository;
    private final OrderItemRepository orderItemRepository;
    private final AccountEventLogger accountEventLogger;
    private final PasswordEncoder passwordEncoder;
    private final TokenEpoch tokenEpoch;
    private final ChatSessionService chatSessionService;
    private final ProfileService profileService;

    @Transactional
    public void withdraw(Long memberId, String password, String clientIp) {
        Member member = memberRepository.findById(memberId)
                .filter(m -> !m.isWithdrawn())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_REQUIRED));
        // 로그인된 기기를 잠깐 빌린 사람이 계정을 날리지 못하게 비밀번호를 다시 받는다
        if (!passwordEncoder.matches(password, member.getPassword())) {
            throw new BusinessException(ErrorCode.MEMBER_PASSWORD_MISMATCH);
        }
        // 배송 중이거나 환불이 걸린 주문이 있으면 연락처가 사라져 처리할 길이 없다
        if (orderItemRepository.countUnfinishedPaidItems(memberId) > 0) {
            throw new BusinessException(ErrorCode.MEMBER_HAS_ACTIVE_ORDERS);
        }

        cartItemRepository.deleteAllOfMember(memberId);
        wishlistRepository.deleteAllOfMember(memberId);
        addressRepository.deleteAllOfMember(memberId);
        refreshTokenRepository.deleteAllOfMember(memberId);
        // 벌크 DELETE가 영속성 컨텍스트를 비웠으므로 다시 읽어 바꾼다
        memberRepository.findById(memberId).orElseThrow()
                .withdraw(passwordEncoder.encode(UUID.randomUUID().toString()), LocalDateTime.now());
        accountEventLogger.log(memberId, AccountEventType.WITHDRAW, clientIp);

        // 이미 나가 있는 AT를 즉시 끊는다 — 로그아웃과 같은 장치(07 §3-2)
        tokenEpoch.invalidate(memberId);
        chatSessionService.endSessionAsync(ChatIdentity.member(memberId), SessionEndReason.LOGOUT);
        purgeAiProfileAfterCommit(memberId);
    }

    /**
     * AI 서버의 취향·대화 기록 삭제는 커밋 뒤에 한다 — 탈퇴가 롤백됐는데 AI 쪽만 지워지면 안 되고,
     * AI 장애가 탈퇴를 막아서도 안 된다. 실패하면 회원 id를 남겨 수동으로 지울 수 있게 한다.
     */
    private void purgeAiProfileAfterCommit(Long memberId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    profileService.purgeForWithdrawal(memberId);
                } catch (Exception e) {
                    log.warn("탈퇴 회원의 AI 취향 데이터 삭제 실패 — 수동 삭제 필요 (memberId={})", memberId, e);
                }
            }
        });
    }
}
