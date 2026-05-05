package com.skala.backend.project.dto;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;
import com.skala.backend.project.repository.ProjectCardRow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectCardResponse(
		Long id,
		String projectName,
		List<String> assigneeNames,
		long assigneeCount,
		String contractNo,
		LocalDate constructionStartDate,
		LocalDate constructionEndDate,
		BigDecimal latestCumulativeProgressRate,
		ProjectStatusCode status,
		boolean hasActionRequest
) {

	public static ProjectCardResponse from(ProjectCardRow row) {
		return new ProjectCardResponse(
				row.id(),
				row.projectName(),
				row.assigneeNames(),
				row.assigneeCount(),
				row.contractNo(),
				row.constructionStartDate(),
				row.constructionEndDate(),
				row.latestCumulativeProgressRate(),
				row.status(),
				row.hasActionRequest()
		);
	}

	public static ProjectCardResponse of(
			Project project,
			long assigneeCount,
			BigDecimal latestCumulativeProgressRate,
			boolean hasActionRequest
	) {
		return new ProjectCardResponse(
				project.getId(),
				project.getProjectName(),
				List.of(),
				assigneeCount,
				project.getContractNo(),
				project.getConstructionStartDate(),
				project.getConstructionEndDate(),
				latestCumulativeProgressRate,
				project.getStatus(),
				hasActionRequest
		);
	}
}
