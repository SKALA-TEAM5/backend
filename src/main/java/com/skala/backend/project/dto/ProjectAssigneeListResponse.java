package com.skala.backend.project.dto;

import java.util.List;

public record ProjectAssigneeListResponse(
		Long projectId,
		List<ProjectAssigneeResponse> assignees
) {
}
