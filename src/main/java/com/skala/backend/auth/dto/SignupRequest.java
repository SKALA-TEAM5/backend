package com.skala.backend.auth.dto;

import com.skala.backend.user.domain.RoleCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SignupRequest(
		@NotBlank
		@Size(max = 50)
		String employeeNo,

		@NotBlank
		@Size(max = 100)
		String realName,

		@NotBlank
		@Size(min = 8)
		String password,

		@NotNull
		RoleCode roleCode
) {
}
