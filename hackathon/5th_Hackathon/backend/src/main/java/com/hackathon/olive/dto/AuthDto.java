package com.hackathon.olive.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDto {
    private AuthDto() {}

    public record SignupRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotBlank @Size(max = 50) String name) {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password) {}

    public record UserResponse(Long id, String email, String name, String role) {}

    /** BFF(Next.js)가 토큰을 받아 httpOnly 쿠키로 저장. user는 화면 표시용. */
    public record TokenResponse(String accessToken, String refreshToken, UserResponse user) {}

    public record RefreshRequest(String refreshToken) {}
}
