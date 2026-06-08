package com.hackathon.openrun.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 회원가입 요청 (TECH_SPEC §5 POST /signup).
 * role 은 USER 또는 HOST 만 허용(ADMIN 가입 차단).
 */
@Getter
@Setter
public class SignupRequest {

    @NotBlank(message = "아이디를 입력하세요.")
    @Size(min = 3, max = 50, message = "아이디는 3~50자입니다.")
    private String username;

    @NotBlank(message = "비밀번호를 입력하세요.")
    @Size(min = 4, max = 72, message = "비밀번호는 4자 이상입니다.")
    private String password;

    @NotBlank(message = "닉네임을 입력하세요.")
    @Size(min = 1, max = 50)
    private String nickname;

    @NotBlank
    @Pattern(regexp = "USER|HOST", message = "역할은 USER 또는 HOST 입니다.")
    private String role;
}
