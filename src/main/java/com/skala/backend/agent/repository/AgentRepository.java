package com.skala.backend.agent.repository;

import com.skala.backend.agent.dto.AgentDtos.ValidationLogCommand;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.usage.domain.UsageStatement;
import com.skala.backend.usage.domain.UsageStatementItem;
import com.skala.backend.usage.domain.UsageStatementSummary;
import com.skala.backend.usage.repository.UsageStatementItemRepository;
import com.skala.backend.usage.repository.UsageStatementRepository;
import com.skala.backend.usage.repository.UsageStatementSummaryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AgentRepository {

	private final UsageStatementRepository usageStatementRepository;
	private final UsageStatementItemRepository usageStatementItemRepository;
	private final UsageStatementSummaryRepository usageStatementSummaryRepository;
	private final JdbcTemplate jdbcTemplate;

	public AgentRepository(
			UsageStatementRepository usageStatementRepository,
			UsageStatementItemRepository usageStatementItemRepository,
			UsageStatementSummaryRepository usageStatementSummaryRepository,
			JdbcTemplate jdbcTemplate
	) {
		this.usageStatementRepository = usageStatementRepository;
		this.usageStatementItemRepository = usageStatementItemRepository;
		this.usageStatementSummaryRepository = usageStatementSummaryRepository;
		this.jdbcTemplate = jdbcTemplate;
	}

	public UsageStatement requireUsageStatement(Long projectId, Long usageStatementId) {
		return usageStatementRepository.findByIdAndProjectId(usageStatementId, projectId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서를 찾을 수 없습니다."));
	}

	public List<UsageStatementItem> findUsageItems(Long usageStatementId) {
		return usageStatementItemRepository.findByUsageStatementIdOrderByPageNoAscUsedOnAscIdAsc(usageStatementId);
	}

	public UsageStatementItem requireProjectItem(Long projectId, Long itemId) {
		return usageStatementItemRepository.findProjectItem(projectId, itemId)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용내역서 상세항목을 찾을 수 없습니다."));
	}

	public List<UsageStatementSummary> findUsageSummaries(Long usageStatementId) {
		return usageStatementSummaryRepository.findByUsageStatementIdOrderByCategoryCodeAsc(usageStatementId);
	}

	public Long saveValidationLog(ValidationLogCommand command) {
		String sql = """
				INSERT INTO service.validation_logs (
					project_id,
					usage_statement_id,
					usage_statement_item_id,
					validation_type_code,
					result_code,
					details,
					model_name,
					agent_type_code,
					log_type_code,
					severity_code
				) VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
				RETURNING id
				""";
		return jdbcTemplate.queryForObject(
				sql,
				Long.class,
				command.projectId(),
				command.usageStatementId(),
				command.usageStatementItemId(),
				command.validationTypeCode(),
				command.resultCode(),
				command.detailsJson(),
				command.modelName(),
				command.agentTypeCode(),
				command.logTypeCode(),
				command.severityCode()
		);
	}
}
