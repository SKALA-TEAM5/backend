package com.skala.backend.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
    @Convert(converter = AgentTypeCodeConverter.class)
    private AgentTypeCode agentTypeCode;

    @Column(name = "status_code", nullable = false, length = 20)
    @Convert(converter = AgentLogStatusConverter.class)
    private AgentLogStatus status;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "usage_statement_item_id")
    private Long usageStatementItemId;

    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentLog() {}

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUsageStatementId() { return usageStatementId; }
    public Long getUsageStatementItemId() { return usageStatementItemId; }
    public String getAgentTypeCode() { return agentTypeCode.getCode(); }
    public String getStatusCode() { return status.getCode(); }
    public String getModelName() { return modelName; }
    public String getDetails() { return details; }
    public UUID getRunId() { return runId; }
    public Instant getCreatedAt() { return createdAt; }
}
