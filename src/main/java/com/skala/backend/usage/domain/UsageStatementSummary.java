package com.skala.backend.usage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "usage_statement_summaries", schema = "service")
public class UsageStatementSummary {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usage_statement_id", nullable = false)
	private Long usageStatementId;

	@Column(name = "category_code", nullable = false, length = 50)
	private String categoryCode;

	@Column(name = "previous_amount", nullable = false)
	private BigDecimal previousAmount;

	@Column(name = "current_amount", nullable = false)
	private BigDecimal currentAmount;

	@Column(name = "cumulative_amount", nullable = false)
	private BigDecimal cumulativeAmount;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UsageStatementSummary() {
	}

	public Long getId() { return id; }
	public Long getUsageStatementId() { return usageStatementId; }
	public String getCategoryCode() { return categoryCode; }
	public BigDecimal getPreviousAmount() { return previousAmount; }
	public BigDecimal getCurrentAmount() { return currentAmount; }
	public BigDecimal getCumulativeAmount() { return cumulativeAmount; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
