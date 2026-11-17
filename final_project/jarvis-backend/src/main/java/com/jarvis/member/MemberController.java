package com.jarvis.member;

import com.jarvis.global.auth.AccessCookieManager;
import com.jarvis.global.auth.AuthUser;
import com.jarvis.global.auth.ClientIp;
import com.jarvis.global.auth.RefreshCookieManager;
import com.jarvis.global.response.ApiResponse;
import com.jarvis.member.dto.WithdrawRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 본인 계정 — 🔑 USER 전용(SecurityConfig /api/members/**) */
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final WithdrawalService withdrawalService;
    private final AccessCookieManager accessCookieManager;
    private final RefreshCookieManager refreshCookieManager;
    private final ClientIp clientIp;

    /** M-17 탈퇴 (02 D55). 성공하면 로그아웃과 같이 인증 쿠키를 지운다 */
    @PostMapping("/me/withdraw")
    public ApiResponse<Void> withdraw(@Valid @RequestBody WithdrawRequest request,
                                      @AuthenticationPrincipal AuthUser authUser,
                                      HttpServletRequest httpRequest,
                                      HttpServletResponse httpResponse) {
        withdrawalService.withdraw(authUser.memberId(), request.password(), clientIp.resolve(httpRequest));
        accessCookieManager.expire(httpResponse);
        refreshCookieManager.expire(httpResponse);
        return ApiResponse.success(null);
    }
}
