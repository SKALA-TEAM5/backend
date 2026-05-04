package com.skala.backend.project.dto;

import com.skala.backend.project.domain.ProjectStatusCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectCreateRequest(
		@Size(max = 100)
		String contractNo,

		@NotBlank
		@Size(max = 200)
		String constructionCompany,

		@NotBlank
		@Size(max = 300)
		String projectName,

		@NotBlank
		@Size(max = 500)
		String siteLocation,

		@Size(max = 100)
		String representativeName,

		@NotNull
		@DecimalMin("0")
		BigDecimal contractAmount,

		@NotNull
		LocalDate constructionStartDate,

		@NotNull
		LocalDate constructionEndDate,

		@Size(max = 200)
		String clientName,

		@NotNull
		@DecimalMin("0")
		BigDecimal appropriatedAmount,

		ProjectStatusCode status
) {
}
