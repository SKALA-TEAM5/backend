package com.skala.backend.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "agent_usage_records", schema = "service")
public class AgentUsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "usage_statement_id")
    private Long usageStatementId;

    @Column(name = "agent_type_code", nullable = false, length = 20)
    private String agentTypeCode;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "input_tokens")
    private Long inputTokens;

    @Column(name = "output_tokens")
    private Long outputTokens;

    @Column(name = "cost_usd", precision = 12, scale = 8)
    private BigDecimal costUsd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AgentUsageRecord() {}

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getProjectId() { return projectId; }
    public Long getUsageStatementId() { return usageStatementId; }
    public String getAgentTypeCode() { return agentTypeCode; }
    public String getModelName() { return modelName; }
    public Long getInputTokens() { return inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public BigDecimal getCostUsd() { return costUsd; }
    public Instant getCreatedAt() { return createdAt; }
}
