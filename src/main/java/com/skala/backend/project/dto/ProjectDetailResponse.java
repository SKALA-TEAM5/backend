package com.skala.backend.project.dto;

import com.skala.backend.project.domain.Project;
import com.skala.backend.project.domain.ProjectStatusCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProjectDetailResponse(
		Long id,
		String contractNo,
		String constructionCompany,
		String projectName,
		String siteLocation,
		String representativeName,
		BigDecimal contractAmount,
		LocalDate constructionStartDate,
		LocalDate constructionEndDate,
		String clientName,
		BigDecimal appropriatedAmount,
		ProjectStatusCode status,
		List<ProjectAssigneeResponse> assignees,
		Instant createdAt,
		Instant updatedAt
) {

	public static ProjectDetailResponse of(Project project, List<ProjectAssigneeResponse> assignees) {
		return new ProjectDetailResponse(
				project.getId(),
				project.getContractNo(),
				project.getConstructionCompany(),
				project.getProjectName(),
				project.getSiteLocation(),
				project.getRepresentativeName(),
				project.getContractAmount(),
				project.getConstructionStartDate(),
				project.getConstructionEndDate(),
				project.getClientName(),
				project.getAppropriatedAmount(),
				project.getStatus(),
				assignees,
				project.getCreatedAt(),
				project.getUpdatedAt()
		);
	}
}
