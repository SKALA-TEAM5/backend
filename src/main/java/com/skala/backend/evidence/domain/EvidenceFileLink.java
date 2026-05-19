package com.skala.backend.evidence.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "evidence_file_links", schema = "service")
public class EvidenceFileLink {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "usage_statement_item_id", nullable = false)
	private Long usageStatementItemId;

	@Column(name = "file_id", nullable = false)
	private Long fileId;

	@Column(name = "evidence_type_code", nullable = false, length = 30)
	private String evidenceTypeCode;

	@Column(name = "checked_at")
	private Instant checkedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected EvidenceFileLink() {
	}

	private EvidenceFileLink(Long usageStatementItemId, Long fileId, String evidenceTypeCode) {
		this.usageStatementItemId = usageStatementItemId;
		this.fileId = fileId;
		this.evidenceTypeCode = evidenceTypeCode;
	}

	public static EvidenceFileLink create(Long usageStatementItemId, Long fileId, String evidenceTypeCode) {
		return new EvidenceFileLink(usageStatementItemId, fileId, evidenceTypeCode);
	}

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

	public void moveTo(Long targetItemId, String evidenceTypeCode) {
		this.usageStatementItemId = targetItemId;
		this.evidenceTypeCode = evidenceTypeCode;
	}

	public Long getId() { return id; }
	public Long getUsageStatementItemId() { return usageStatementItemId; }
	public Long getFileId() { return fileId; }
	public String getEvidenceTypeCode() { return evidenceTypeCode; }
	public Instant getCheckedAt() { return checkedAt; }
	public Instant getCreatedAt() { return createdAt; }
	public Instant getUpdatedAt() { return updatedAt; }
}
