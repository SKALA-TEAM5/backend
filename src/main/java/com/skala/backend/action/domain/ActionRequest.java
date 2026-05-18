package com.skala.backend.action.domain;

import com.skala.backend.global.error.ApiException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "action_requests", schema = "service")
public class ActionRequest {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "usage_statement_id")
	private Long usageStatementId;

	@Column(name = "usage_statement_item_id")
	private Long usageStatementItemId;

	@Column(name = "requested_by_user_id", nullable = false)
	private Long requestedByUserId;

	@Column(name = "assignee_user_id")
	private Long assigneeUserId;

	@Column(name = "title", nullable = false, length = 300)
	private String title;

	@Column(name = "reason")
	private String reason;

	@Column(name = "status_code", nullable = false, length = 30)
	private String statusCode;

	@Column(name = "due_date")
	private LocalDate dueDate;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	protected ActionRequest() {}

	public static ActionRequest create(
			Long projectId,
			Long requestedByUserId,
			Long assigneeUserId,
			Long usageStatementId,
			Long usageStatementItemId,
			String title,
			String reason,
			LocalDate dueDate
	) {
		ActionRequest ar = new ActionRequest();
		ar.projectId = projectId;
		ar.requestedByUserId = requestedByUserId;
		ar.assigneeUserId = assigneeUserId;
		ar.usageStatementId = usageStatementId;
		ar.usageStatementItemId = usageStatementItemId;
		ar.title = title;
		ar.reason = reason;
		ar.statusCode = "open";
		ar.dueDate = dueDate;
		ar.createdAt = Instant.now();
		return ar;
	}

	public void updateStatus(String newStatusCode) {
		switch (newStatusCode) {
			case "in_progress" -> {
				if (!"open".equals(statusCode)) {
					throw new ApiException(HttpStatus.CONFLICT, "open 상태에서만 진행 중으로 변경할 수 있습니다.");
				}
			}
			case "resolved" -> {
				if (!"in_progress".equals(statusCode)) {
					throw new ApiException(HttpStatus.CONFLICT, "진행 중 상태에서만 처리 완료로 변경할 수 있습니다.");
				}
				this.resolvedAt = Instant.now();
			}
			case "closed" -> {
				if (!"resolved".equals(statusCode)) {
					throw new ApiException(HttpStatus.CONFLICT, "처리 완료 상태에서만 종료할 수 있습니다.");
				}
				if (this.resolvedAt == null) {
					this.resolvedAt = Instant.now();
				}
			}
			default -> throw new ApiException(HttpStatus.BAD_REQUEST, "유효하지 않은 상태 코드입니다.");
		}
		this.statusCode = newStatusCode;
	}

	public Long getId() { return id; }
	public Long getProjectId() { return projectId; }
	public Long getUsageStatementId() { return usageStatementId; }
	public Long getUsageStatementItemId() { return usageStatementItemId; }
	public Long getRequestedByUserId() { return requestedByUserId; }
	public Long getAssigneeUserId() { return assigneeUserId; }
	public String getTitle() { return title; }
	public String getReason() { return reason; }
	public String getStatusCode() { return statusCode; }
	public LocalDate getDueDate() { return dueDate; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getResolvedAt() { return resolvedAt; }
}
