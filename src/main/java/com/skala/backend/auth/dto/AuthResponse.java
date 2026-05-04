package com.skala.backend.auth.dto;

import com.skala.backend.user.dto.UserProfileResponse;

public record AuthResponse(
		UserProfileResponse user
) {
}
