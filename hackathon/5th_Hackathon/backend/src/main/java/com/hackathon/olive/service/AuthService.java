package com.hackathon.olive.service;

import com.hackathon.olive.domain.RefreshToken;
import com.hackathon.olive.domain.User;
import com.hackathon.olive.dto.AuthDto.*;
import com.hackathon.olive.repository.RefreshTokenRepository;
import com.hackathon.olive.repository.UserRepository;
import com.hackathon.olive.security.JwtTokenProvider;
import com.hackathon.olive.security.TokenBlacklistService;
import com.hackathon.olive.web.ApiException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider tokenProvider;
    private final TokenBlacklistService blacklistService;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-token-validity-seconds}")
    private long refreshValiditySeconds;

    @Transactional
    public UserResponse signup(SignupRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw ApiException.conflict("이미 가입된 이메일입니다.");
        }
        User user = User.builder()
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .name(req.name())
                .role("USER")
                .build();
        userRepository.save(user);
        return toUserResponse(user);
    }

    /**
     * 로그인. JWT(스테이트리스 액세스) + 리프레시 토큰 + Spring 세션(서버측)을 함께 발급.
     */
    @Transactional
    public TokenResponse login(LoginRequest req, HttpSession session) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다."));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw ApiException.unauthorized("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());

        // 리프레시 토큰: 사용자당 1개 유지(재로그인 시 회전)
        refreshTokenRepository.deleteByUserId(user.getId());
        String refresh = UUID.randomUUID().toString();
        refreshTokenRepository.save(new RefreshToken(
                refresh, user.getId(), Instant.now().plusSeconds(refreshValiditySeconds)));

        // Spring 세션(서버측, H2에 영속)에 로그인 사용자 기록 → 'JWT + 세션' 동시 활용
        session.setAttribute("userId", user.getId());
        session.setAttribute("email", user.getEmail());

        return new TokenResponse(accessToken, refresh, toUserResponse(user));
    }

    /** 리프레시 토큰으로 액세스 토큰 재발급. */
    @Transactional
    public TokenResponse refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("리프레시 토큰이 없습니다.");
        }
        RefreshToken rt = refreshTokenRepository.findById(refreshToken)
                .orElseThrow(() -> ApiException.unauthorized("유효하지 않은 리프레시 토큰입니다."));
        if (rt.isExpired()) {
            refreshTokenRepository.delete(rt);
            throw ApiException.unauthorized("리프레시 토큰이 만료되었습니다.");
        }
        User user = userRepository.findById(rt.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("사용자를 찾을 수 없습니다."));
        String accessToken = tokenProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole());
        return new TokenResponse(accessToken, refreshToken, toUserResponse(user));
    }

    /**
     * 로그아웃. 3중 무효화:
     *  1) 액세스 토큰 jti → 블랙리스트(만료 전 강제 무효화)
     *  2) 리프레시 토큰 삭제(재발급 차단)
     *  3) Spring 세션 무효화
     */
    @Transactional
    public void logout(String accessToken, HttpSession session) {
        if (accessToken != null) {
            try {
                Claims claims = tokenProvider.parse(accessToken);
                Instant exp = claims.getExpiration().toInstant();
                blacklistService.blacklist(claims.getId(), exp);
                refreshTokenRepository.deleteByUserId(tokenProvider.getUserId(claims));
            } catch (Exception ignored) {
                // 이미 만료/위조된 토큰이면 블랙리스트 불필요
            }
        }
        if (session != null) {
            session.invalidate();
        }
    }

    public UserResponse me(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("로그인이 필요합니다."));
        return toUserResponse(user);
    }

    private UserResponse toUserResponse(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getName(), u.getRole());
    }
}
