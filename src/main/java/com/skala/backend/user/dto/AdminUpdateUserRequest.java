package com.skala.backend.user.dto;

import com.skala.backend.user.domain.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 사용자 수정 요청. 변경할 필드만 전달합니다.")
public record AdminUpdateUserRequest(
		@Schema(description = "변경할 사용자 실명", example = "이현장")
		@Size(max = 100)
		String realName,

		@Schema(description = "변경할 비밀번호. 전달하지 않으면 기존 비밀번호를 유지합니다.", example = "newPassword123")
		@Size(min = 8)
		String password,

		@Schema(description = "변경할 역할 코드", example = "hq")
		RoleCode roleCode
) {

	public boolean isEmpty() {
		return realName == null && password == null && roleCode == null;
	}
}
