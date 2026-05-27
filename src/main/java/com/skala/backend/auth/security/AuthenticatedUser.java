package com.skala.backend.auth.security;

import com.skala.backend.user.domain.RoleCode;

public record AuthenticatedUser(
		Long id,
		RoleCode roleCode
) {
}
