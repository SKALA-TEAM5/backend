package com.skala.backend.project.dto;

import com.skala.backend.project.domain.ProjectStatusCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "프로젝트 수정 요청. 변경할 필드만 전달합니다.")
public record ProjectUpdateRequest(
		@Schema(description = "계약번호", example = "CN-2026-001")
		@Size(max = 100)
		String contractNo,

		@Schema(description = "시공사명", example = "스칼라건설")
		@Size(max = 200)
		String constructionCompany,

		@Schema(description = "프로젝트명", example = "스마트 안전관리 시스템 구축")
		@Size(max = 300)
		String projectName,

		@Schema(description = "현장 위치", example = "서울특별시 강남구 테헤란로 123")
		@Size(max = 500)
		String siteLocation,

		@Schema(description = "대표자명", example = "홍길동")
		@Size(max = 100)
		String representativeName,

		@Schema(description = "계약금액", example = "1200000000")
		@DecimalMin("0")
		BigDecimal contractAmount,

		@Schema(description = "공사 시작일", example = "2026-01-01")
		LocalDate constructionStartDate,

		@Schema(description = "공사 종료일", example = "2026-12-31")
		LocalDate constructionEndDate,

		@Schema(description = "발주처명", example = "스칼라시")
		@Size(max = 200)
		String clientName,

		@Schema(description = "책정 예산", example = "1500000000")
		@DecimalMin("0")
		BigDecimal appropriatedAmount,

		@Schema(description = "프로젝트 상태", example = "completed")
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
