package com.skala.backend.user.dto;

import jakarta.validation.constraints.NotBlank;

public record WithdrawRequest(
		@NotBlank
		String password
) {
}
