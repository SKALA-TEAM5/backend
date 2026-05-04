package com.skala.backend.project.dto;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectCardResponse(
		Long id,
		String projectName,
		long assigneeCount,
		String contractNo,
		LocalDate constructionStartDate,
		LocalDate constructionEndDate,
		BigDecimal latestCumulativeProgressRate,
		ProjectStatusCode status,
		boolean hasActionRequest
) {

	public static ProjectCardResponse of(
			Project project,
			long assigneeCount,
			BigDecimal latestCumulativeProgressRate,
			boolean hasActionRequest
	) {
		return new ProjectCardResponse(
				project.getId(),
				project.getProjectName(),
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
