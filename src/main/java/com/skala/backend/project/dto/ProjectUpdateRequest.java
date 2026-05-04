package com.skala.backend.project.dto;

import com.skala.backend.project.domain.ProjectStatusCode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProjectUpdateRequest(
		@Size(max = 100)
		String contractNo,

		@Size(max = 200)
		String constructionCompany,

		@Size(max = 300)
		String projectName,

		@Size(max = 500)
		String siteLocation,

		@Size(max = 100)
		String representativeName,

		@DecimalMin("0")
		BigDecimal contractAmount,

		LocalDate constructionStartDate,

		LocalDate constructionEndDate,

		@Size(max = 200)
		String clientName,

		@DecimalMin("0")
		BigDecimal appropriatedAmount,

		ProjectStatusCode status
) {

	public boolean isEmpty() {
		return contractNo == null
				&& constructionCompany == null
				&& projectName == null
				&& siteLocation == null
				&& representativeName == null
				&& contractAmount == null
				&& constructionStartDate == null
				&& constructionEndDate == null
				&& clientName == null
				&& appropriatedAmount == null
				&& status == null;
	}
}
