package com.skala.backend.project.repository;

import com.skala.backend.project.domain.ProjectSort;
import com.skala.backend.project.domain.ProjectStatusCode;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

public interface ProjectRepositoryCustom {

	Page<ProjectCardRow> searchCards(
			String keywordPattern,
			String projectNamePattern,
			String contractNoPattern,
			Long assigneeUserId,
			ProjectStatusCode status,
			LocalDate periodFrom,
			LocalDate periodTo,
			Long visibleUserId,
			ProjectSort sort,
			int page,
			int size
	);
}
