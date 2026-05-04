package com.skala.backend.project.dto;

import com.skala.backend.project.domain.ProjectUserAssignment;
import com.skala.backend.user.domain.RoleCode;

import java.time.Instant;

public record ProjectAssigneeResponse(
		Long userId,
		String employeeNo,
		String realName,
		RoleCode roleCode,
		Instant assignedAt,
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
