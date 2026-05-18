package com.skala.backend.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "agent_logs", schema = "service")
public class AgentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "usage_statement_id")
    private Long usageStatementId;

    @Column(name = "agent_type_code", nullable = false, length = 20)
    private String agentTypeCode;

    @Column(name = "status_code", nullable = false, length = 20)
    private String statusCode;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentLog() {}

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUsageStatementId() { return usageStatementId; }
    public String getAgentTypeCode() { return agentTypeCode; }
    public String getStatusCode() { return statusCode; }
    public String getModelName() { return modelName; }
    public UUID getRunId() { return runId; }
    public Instant getCreatedAt() { return createdAt; }
}
