package com.skala.backend.project.dto;

import java.util.List;

public record ProjectListResponse(
		int page,
		int size,
		long totalCount,
		int totalPages,
		List<ProjectCardResponse> items
) {
}
