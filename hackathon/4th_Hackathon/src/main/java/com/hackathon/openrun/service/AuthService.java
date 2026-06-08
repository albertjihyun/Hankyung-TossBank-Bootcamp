package com.hackathon.openrun.service;

import com.hackathon.openrun.domain.Member;
import com.hackathon.openrun.domain.Role;
import com.hackathon.openrun.dto.SignupRequest;
import com.hackathon.openrun.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 비즈니스 로직 (TECH_SPEC §1 계층 규칙: 트랜잭션은 Service).
 */
@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Long signup(SignupRequest req) {
        if (memberRepository.existsByUsername(req.getUsername())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        Role role = Role.valueOf(req.getRole()); // USER/HOST (검증은 DTO @Pattern)
        Member member = Member.builder()
                .username(req.getUsername())
                .password(passwordEncoder.encode(req.getPassword())) // BCrypt 인코딩
                .nickname(req.getNickname())
                .role(role)
                .build();
        return memberRepository.save(member).getId();
    }
}
