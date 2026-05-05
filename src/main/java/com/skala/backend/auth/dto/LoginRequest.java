package com.skala.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그인 요청")
public record LoginRequest(
		@Schema(description = "로그인에 사용하는 사번", example = "EMP001")
		@NotBlank
		@Size(max = 50)
		String employeeNo,

		@Schema(description = "비밀번호", example = "password123")
		@NotBlank
		String password
) {
}
