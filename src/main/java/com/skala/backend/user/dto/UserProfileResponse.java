package com.skala.backend.user.dto;

import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;

import java.time.Instant;

public record UserProfileResponse(
		Long id,
		String employeeNo,
		String realName,
		RoleCode roleCode,
		Instant createdAt,
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
