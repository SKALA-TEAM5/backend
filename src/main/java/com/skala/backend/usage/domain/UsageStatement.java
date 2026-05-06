package com.skala.backend.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "usage_statements", schema = "service")
public class UsageStatement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "source_file_id")
	private Long sourceFileId;

	@Column(name = "report_month", nullable = false)
	private LocalDate reportMonth;

	@Column(name = "revision_no", nullable = false)
	private Integer revisionNo;

	@Column(name = "document_written_date", nullable = false)
	private LocalDate documentWrittenDate;

	@Column(name = "cumulative_progress_rate", nullable = false)
	private BigDecimal cumulativeProgressRate;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UsageStatement() {
	}

	public Long getId() { return id; }
	public Long getProjectId() { return projectId; }
	public Long getSourceFileId() { return sourceFileId; }
	public LocalDate getReportMonth() { return reportMonth; }
	public Integer getRevisionNo() { return revisionNo; }
	public LocalDate getDocumentWrittenDate() { return documentWrittenDate; }
	public BigDecimal getCumulativeProgressRate() { return cumulativeProgressRate; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
