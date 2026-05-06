package com.skala.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evidence_requirements", schema = "service")
public class EvidenceRequirement {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usage_statement_item_id", nullable = false)
	private Long usageStatementItemId;

	@Column(name = "evidence_type_code", nullable = false, length = 30)
	private String evidenceTypeCode;

	@Column(name = "is_satisfied", nullable = false)
	private boolean satisfied;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	protected EvidenceRequirement() {
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public void updateSatisfied(boolean satisfied) {
		this.satisfied = satisfied;
	}

	public Long getId() { return id; }
	public Long getUsageStatementItemId() { return usageStatementItemId; }
	public String getEvidenceTypeCode() { return evidenceTypeCode; }
	public boolean isSatisfied() { return satisfied; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
	public boolean isActive() { return active; }
}
