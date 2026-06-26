package com.hackathon.olive.seed;

import com.hackathon.olive.domain.User;
import com.hackathon.olive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 데모 계정 1개 생성(테스트 로그인용). 이미 있으면 스킵. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DemoUserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String email = "demo@olive.com";
        if (userRepository.existsByEmail(email)) return;
        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode("olive1234"))
                .name("올리브")
                .role("USER")
                .build());
        log.info("[seed] demo user created: {} / olive1234", email);
    }
}
