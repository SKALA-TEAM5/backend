package com.skala.backend.aiagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "validation_logs", schema = "service")
public class ValidationLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "usage_statement_id")
	private Long usageStatementId;

	@Column(name = "usage_statement_item_id")
	private Long usageStatementItemId;

	@Column(name = "agent_type_code", length = 50)
	private AiAgentTypeCode agentTypeCode;

	@Column(name = "validation_type_code", nullable = false, length = 50)
	private String validationTypeCode;

	@Column(name = "log_type_code", length = 50)
	private String logTypeCode;

	@Column(name = "severity_code", nullable = false, length = 30)
	private String severityCode;

	@Column(name = "result_code", length = 30)
	private String resultCode;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "details", columnDefinition = "jsonb")
	private String details;

	@Column(name = "model_name", length = 100)
	private String modelName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ValidationLog() {
	}

	private ValidationLog(
			Long projectId,
			Long usageStatementId,
			Long usageStatementItemId,
			AiAgentTypeCode agentTypeCode,
			String validationTypeCode,
			String logTypeCode,
			String severityCode,
			String resultCode,
			String details,
			String modelName
	) {
		this.projectId = projectId;
		this.usageStatementId = usageStatementId;
		this.usageStatementItemId = usageStatementItemId;
		this.agentTypeCode = agentTypeCode;
		this.validationTypeCode = validationTypeCode;
		this.logTypeCode = logTypeCode;
		this.severityCode = severityCode == null || severityCode.isBlank() ? "info" : severityCode;
		this.resultCode = resultCode;
		this.details = details;
		this.modelName = modelName;
		this.createdAt = Instant.now();
	}

	public static ValidationLog create(
			Long projectId,
			Long usageStatementId,
			Long usageStatementItemId,
			AiAgentTypeCode agentTypeCode,
			String validationTypeCode,
			String logTypeCode,
			String severityCode,
			String resultCode,
			String details,
			String modelName
	) {
		return new ValidationLog(
				projectId,
				usageStatementId,
				usageStatementItemId,
				agentTypeCode,
				validationTypeCode,
				logTypeCode,
				severityCode,
				resultCode,
				details,
				modelName
		);
	}

	public Long getId() { return id; }
}
