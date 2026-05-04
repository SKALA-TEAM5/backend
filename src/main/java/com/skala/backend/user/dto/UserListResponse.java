package com.skala.backend.user.dto;

import java.util.List;

public record UserListResponse(
		List<UserProfileResponse> items
) {
}
