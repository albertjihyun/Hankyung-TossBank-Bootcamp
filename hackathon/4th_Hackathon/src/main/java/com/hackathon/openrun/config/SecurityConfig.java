package com.hackathon.openrun.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정 (TECH_SPEC §7).
 *
 * <p>인가 매트릭스 / 폼 로그인 / BCrypt / 403 핸들러. 세션은 Spring Session JDBC 가
 * 자동 연동(application.yml store-type=jdbc) → 다중 인스턴스 간 로그인 공유.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 정적/공개
                        .requestMatchers("/", "/login", "/signup", "/css/**", "/js/**",
                                "/actuator/health", "/error/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/events/**").permitAll()
                        .requestMatchers("/api/**").permitAll()
                        // 권한별
                        .requestMatchers("/host/**").hasRole("HOST")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/me/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/events/*/reserve").authenticated()
                        .requestMatchers("/reservations/**").authenticated()
                        // 나머지
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", false)
                        .failureUrl("/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        // 권한 거부 → 커스텀 403 페이지 (TECH_SPEC §7/§10)
                        .accessDeniedPage("/error/403")
                );
        // CSRF 는 기본 활성(TECH_SPEC §5): 폼은 Thymeleaf 가 숨은 토큰을 자동 삽입,
        // /api/** 는 GET 전용이라 영향 없음.

        return http.build();
    }
}
