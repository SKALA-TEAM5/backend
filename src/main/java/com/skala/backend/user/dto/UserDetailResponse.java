package com.skala.backend.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "사용자 상세 응답 데이터")
public record UserDetailResponse(
		@Schema(description = "사용자 프로필")
		UserProfileResponse user
) {
}
