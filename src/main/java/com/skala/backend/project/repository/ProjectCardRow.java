package com.skala.backend.project.repository;

import com.skala.backend.project.domain.ProjectStatusCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProjectCardRow(
		Long id,
		String projectName,
		List<String> assigneeNames,
		long assigneeCount,
		String contractNo,
		LocalDate constructionStartDate,
		LocalDate constructionEndDate,
		BigDecimal latestCumulativeProgressRate,
		ProjectStatusCode status,
		long uncheckedMatchedFileCount,
		String latestUsageStatementStatusCode,
		BigDecimal usageRate,
		boolean needCheck
) {
}
