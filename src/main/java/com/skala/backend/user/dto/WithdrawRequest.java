package com.skala.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "회원탈퇴 요청")
public record WithdrawRequest(
		@Schema(description = "본인 확인용 현재 비밀번호", example = "password123")
		@NotBlank
		String password
) {
}
