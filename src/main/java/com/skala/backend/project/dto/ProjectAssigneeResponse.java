package com.skala.backend.project.dto;

import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.user.domain.RoleCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "프로젝트 담당자 정보")
public record ProjectAssigneeResponse(
		@Schema(description = "담당자 사용자 ID", example = "3")
		Long userId,
		@Schema(description = "담당자 사번", example = "EMP003")
		String employeeNo,
		@Schema(description = "담당자 이름", example = "이현장")
		String realName,
		@Schema(description = "담당자 역할 코드", example = "site")
		RoleCode roleCode,
		@Schema(description = "담당자 배정 일시", example = "2026-05-05T01:00:00Z")
		Instant assignedAt,
		@Schema(description = "담당자를 배정한 사용자 ID", example = "1")
		Long assignedByUserId
) {

	public static ProjectAssigneeResponse from(ProjectUserAssignment assignment) {
		return new ProjectAssigneeResponse(
				assignment.getUser().getId(),
				assignment.getUser().getEmployeeNo(),
				assignment.getUser().getRealName(),
				assignment.getUser().getRoleCode(),
				assignment.getCreatedAt(),
				assignment.getAssignedBy() == null ? null : assignment.getAssignedBy().getId()
		);
	}
}
