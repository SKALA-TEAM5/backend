package com.skala.backend.user.dto;

import com.skala.backend.user.domain.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserRequests {

	private UserRequests() {}

	@Schema(description = "system_admin 계정 발급 요청")
	public record AdminCreateRequest(
			@Schema(description = "로그인에 사용할 사번", example = "EMP010")
			@NotBlank @Size(max = 50) String employeeNo,
			@Schema(description = "사용자 실명", example = "이현장")
			@NotBlank @Size(max = 100) String realName,
			@Schema(description = "8자 이상의 초기 비밀번호", example = "password123")
			@NotBlank @Size(min = 8) String password,
			@Schema(description = "발급할 사용자 역할 코드. system_admin, admin, user, agent 중 하나를 사용합니다.", example = "user")
			@NotNull RoleCode roleCode
	) {}

	@Schema(description = "관리자 사용자 수정 요청. 변경할 필드만 전달합니다.")
	public record AdminUpdateRequest(
			@Schema(description = "변경할 사용자 실명", example = "이현장")
			@Size(max = 100) String realName,
			@Schema(description = "변경할 비밀번호. 전달하지 않으면 기존 비밀번호를 유지합니다.", example = "newPassword123")
			@Size(min = 8) String password,
			@Schema(description = "변경할 역할 코드", example = "admin")
			RoleCode roleCode
	) {
		public boolean isEmpty() {
			return realName == null && password == null && roleCode == null;
		}
	}
}
