package com.skala.backend.auth.dto;

import com.skala.backend.user.dto.UserResponses;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증 성공 응답 데이터")
public record AuthResponse(
		@Schema(description = "인증된 사용자 정보")
		UserResponses.ProfileResponse user
) {
}
