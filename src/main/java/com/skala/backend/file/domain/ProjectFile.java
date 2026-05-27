package com.skala.backend.file.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "files", schema = "service")
public class ProjectFile {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false)
	private Long projectId;

	@Column(name = "uploaded_by_user_id", nullable = false)
	private Long uploadedByUserId;

	@Column(name = "uploaded_evidence_type_code", nullable = false, length = 30)
	private String uploadedEvidenceTypeCode;

	@Column(name = "original_filename", nullable = false, length = 500)
	private String originalFilename;

	@Column(name = "storage_key", nullable = false)
	private String storageKey;

	@Column(name = "mime_type", nullable = false, length = 150)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	@Column(name = "captured_at")
	private Instant capturedAt;

	@Column(name = "uploaded_at", nullable = false)
	private Instant uploadedAt;

	@Column(name = "status_code", nullable = false, length = 20)
	private String statusCode = "draft";

	@Column(name = "deleted_at")
	private Instant deletedAt;

	@Column(name = "deleted_by_user_id")
	private Long deletedByUserId;

	protected ProjectFile() {
	}

	private ProjectFile(Long projectId, Long uploadedByUserId, String uploadedEvidenceTypeCode, String originalFilename, String storageKey, String mimeType, Long sizeBytes, Instant capturedAt) {
		this.projectId = projectId;
		this.uploadedByUserId = uploadedByUserId;
		this.uploadedEvidenceTypeCode = uploadedEvidenceTypeCode;
		this.originalFilename = originalFilename;
		this.storageKey = storageKey;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
		this.capturedAt = capturedAt;
	}

	public static ProjectFile create(Long projectId, Long uploadedByUserId, String uploadedEvidenceTypeCode, String originalFilename, String storageKey, String mimeType, Long sizeBytes, Instant capturedAt) {
		return new ProjectFile(projectId, uploadedByUserId, uploadedEvidenceTypeCode, originalFilename, storageKey, mimeType, sizeBytes, capturedAt);
	}

	@PrePersist
	void prePersist() {
		this.uploadedAt = Instant.now();
	}

	public void markDeleted(Long deletedByUserId) {
		this.deletedAt = Instant.now();
		this.deletedByUserId = deletedByUserId;
	}

	public Long getId() { return id; }
	public Long getProjectId() { return projectId; }
	public Long getUploadedByUserId() { return uploadedByUserId; }
	public String getUploadedEvidenceTypeCode() { return uploadedEvidenceTypeCode; }
	public String getOriginalFilename() { return originalFilename; }
	public String getStorageKey() { return storageKey; }
	public String getMimeType() { return mimeType; }
	public Long getSizeBytes() { return sizeBytes; }
	public Instant getCapturedAt() { return capturedAt; }
	public Instant getUploadedAt() { return uploadedAt; }
	public String getStatusCode() { return statusCode; }
	public Instant getDeletedAt() { return deletedAt; }
	public Long getDeletedByUserId() { return deletedByUserId; }
}
