package com.skala.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "사용자 목록 응답 데이터")
public record UserListResponse(
		@Schema(description = "사용자 목록")
		List<UserProfileResponse> items
) {
}
