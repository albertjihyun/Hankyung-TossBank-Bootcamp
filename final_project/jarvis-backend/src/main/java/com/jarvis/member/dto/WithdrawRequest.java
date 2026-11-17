package com.jarvis.member.dto;

import jakarta.validation.constraints.NotBlank;

/** M-17 탈퇴 — 본인 확인용 현재 비밀번호 */
public record WithdrawRequest(@NotBlank String password) {
}
