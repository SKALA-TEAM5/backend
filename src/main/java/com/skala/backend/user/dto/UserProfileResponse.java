package com.skala.backend.user.dto;

import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "사용자 프로필 정보")
public record UserProfileResponse(
		@Schema(description = "사용자 ID", example = "1")
		Long id,
		@Schema(description = "사번", example = "EMP001")
		String employeeNo,
		@Schema(description = "사용자 실명", example = "김스칼라")
		String realName,
		@Schema(description = "사용자 역할 코드", example = "site")
		RoleCode roleCode,
		@Schema(description = "생성 일시", example = "2026-05-05T01:00:00Z")
		Instant createdAt,
		@Schema(description = "수정 일시", example = "2026-05-05T01:00:00Z")
		Instant updatedAt
) {

	public static UserProfileResponse from(User user) {
		return new UserProfileResponse(
				user.getId(),
				user.getEmployeeNo(),
				user.getRealName(),
				user.getRoleCode(),
				user.getCreatedAt(),
				user.getUpdatedAt()
		);
	}
}
