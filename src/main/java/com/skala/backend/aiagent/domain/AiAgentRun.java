package com.skala.backend.aiagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ai_agent_runs", schema = "service")
public class AiAgentRun {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "requested_by_user_id", nullable = false)
	private Long requestedByUserId;

	@Column(name = "agent_type_code", nullable = false, length = 50)
	private AiAgentTypeCode agentTypeCode;

	@Column(name = "status_code", nullable = false, length = 30)
	private AiAgentRunStatusCode statusCode;

	@Column(name = "error_message")
	private String errorMessage;

	@Column(name = "requested_at", nullable = false, updatable = false)
	private Instant requestedAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected AiAgentRun() {
	}

	private AiAgentRun(Long projectId, Long requestedByUserId, AiAgentTypeCode agentTypeCode, Instant now) {
		this.projectId = projectId;
		this.requestedByUserId = requestedByUserId;
		this.agentTypeCode = agentTypeCode;
		this.statusCode = AiAgentRunStatusCode.REQUESTED;
		this.requestedAt = now;
		this.updatedAt = now;
	}

	public static AiAgentRun request(Long projectId, Long requestedByUserId, AiAgentTypeCode agentTypeCode) {
		return new AiAgentRun(projectId, requestedByUserId, agentTypeCode, Instant.now());
	}

	public void start() {
		Instant now = Instant.now();
		this.statusCode = AiAgentRunStatusCode.RUNNING;
		this.startedAt = now;
		this.updatedAt = now;
	}

	public void complete() {
		Instant now = Instant.now();
		this.statusCode = AiAgentRunStatusCode.COMPLETED;
		this.finishedAt = now;
		this.updatedAt = now;
		this.errorMessage = null;
	}

	public void fail(String errorMessage) {
		Instant now = Instant.now();
		this.statusCode = AiAgentRunStatusCode.FAILED;
		this.finishedAt = now;
		this.updatedAt = now;
		this.errorMessage = errorMessage;
	}

	public Long getId() { return id; }

	public Long getProjectId() { return projectId; }

	public Long getRequestedByUserId() { return requestedByUserId; }

	public AiAgentTypeCode getAgentTypeCode() { return agentTypeCode; }

	public AiAgentRunStatusCode getStatusCode() { return statusCode; }

	public String getErrorMessage() { return errorMessage; }

	public Instant getRequestedAt() { return requestedAt; }

	public Instant getStartedAt() { return startedAt; }

	public Instant getFinishedAt() { return finishedAt; }
}
