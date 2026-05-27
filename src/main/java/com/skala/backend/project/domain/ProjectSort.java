package com.skala.backend.project.domain;

import com.skala.backend.global.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;

import java.util.Arrays;

public enum ProjectSort {
	DEFAULT("default", """
			status_rank ASC,
			latest_progress_rate DESC,
			p.construction_start_date DESC,
			p.id DESC
			"""),
	PROJECT_NAME_ASC("project_name_asc", "p.project_name ASC, p.id DESC"),
	PROJECT_NAME_DESC("project_name_desc", "p.project_name DESC, p.id DESC"),
	PROGRESS_RATE_DESC("progress_rate_desc", "latest_progress_rate DESC, p.id DESC"),
	PROGRESS_RATE_ASC("progress_rate_asc", "latest_progress_rate ASC, p.id DESC"),
	START_DATE_ASC("start_date_asc", "p.construction_start_date ASC, p.id DESC"),
	START_DATE_DESC("start_date_desc", "p.construction_start_date DESC, p.id DESC"),
	END_DATE_ASC("end_date_asc", "p.construction_end_date ASC, p.id DESC"),
	END_DATE_DESC("end_date_desc", "p.construction_end_date DESC, p.id DESC");

	private final String value;
	private final String orderByClause;

	ProjectSort(String value, String orderByClause) {
		this.value = value;
		this.orderByClause = orderByClause;
	}

	public static ProjectSort from(String value) {
		if (!StringUtils.hasText(value)) {
			return DEFAULT;
		}

		return Arrays.stream(values())
				.filter(sort -> sort.value.equals(value))
				.findFirst()
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 sort입니다."));
	}

	public String orderByClause() {
		return orderByClause;
	}
}
