package com.skala.backend.user.dto;

import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

public final class UserResponses {

	private UserResponses() {}

	@Schema(description = "사용자 프로필 정보")
	public record ProfileResponse(
			@Schema(description = "사용자 ID", example = "1") Long id,
			@Schema(description = "사번", example = "EMP001") String employeeNo,
			@Schema(description = "사용자 실명", example = "김스칼라") String realName,
			@Schema(description = "사용자 역할 코드", example = "user") RoleCode roleCode,
			@Schema(description = "생성 일시", example = "2026-05-05T01:00:00Z") Instant createdAt,
			@Schema(description = "수정 일시", example = "2026-05-05T01:00:00Z") Instant updatedAt
	) {
		public static ProfileResponse from(User user) {
			return new ProfileResponse(
					user.getId(), user.getEmployeeNo(), user.getRealName(),
					user.getRoleCode(), user.getCreatedAt(), user.getUpdatedAt()
			);
		}
	}

	@Schema(name = "UserDetailResponse", description = "사용자 상세 응답 데이터")
	public record DetailResponse(
			@Schema(description = "사용자 프로필") ProfileResponse user
	) {}

	@Schema(name = "UserListResponse", description = "사용자 목록 응답 데이터")
	public record ListResponse(
			@Schema(description = "사용자 목록") List<ProfileResponse> items
	) {}
}
