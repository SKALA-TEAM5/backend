package com.skala.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank
		@Size(max = 50)
		String employeeNo,

		@NotBlank
		String password
) {
}
