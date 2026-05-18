package com.skala.backend.usage.domain;

import com.skala.backend.global.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

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

	@Column(name = "status_code", nullable = false, length = 30)
	private String statusCode;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UsageStatement() {
	}

	public void submit() {
		if (!"draft".equals(statusCode)) {
			throw new ApiException(HttpStatus.CONFLICT, "작성 중인 사용내역서만 제출할 수 있습니다.");
		}
		this.statusCode = "upload_completed";
	}

	public void requestSupplement() {
		if (!"upload_completed".equals(statusCode)) {
			throw new ApiException(HttpStatus.CONFLICT, "제출된 사용내역서에만 보완 요청할 수 있습니다.");
		}
		this.statusCode = "supplement_required";
	}

	public void completeReview() {
		if (!"upload_completed".equals(statusCode) && !"supplement_required".equals(statusCode)) {
			throw new ApiException(HttpStatus.CONFLICT, "검토 가능한 상태의 사용내역서가 아닙니다.");
		}
		this.statusCode = "review_completed";
	}

	public Long getId() { return id; }
	public Long getProjectId() { return projectId; }
	public Long getSourceFileId() { return sourceFileId; }
	public LocalDate getReportMonth() { return reportMonth; }
	public Integer getRevisionNo() { return revisionNo; }
	public LocalDate getDocumentWrittenDate() { return documentWrittenDate; }
	public BigDecimal getCumulativeProgressRate() { return cumulativeProgressRate; }
	public String getStatusCode() { return statusCode; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
