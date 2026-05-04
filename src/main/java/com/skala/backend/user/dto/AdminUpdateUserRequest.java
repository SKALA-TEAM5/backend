package com.skala.backend.user.dto;

import com.skala.backend.user.domain.RoleCode;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
		@Size(max = 100)
		String realName,

		@Size(min = 8)
		String password,

		RoleCode roleCode
) {

	public boolean isEmpty() {
		return realName == null && password == null && roleCode == null;
	}
}
