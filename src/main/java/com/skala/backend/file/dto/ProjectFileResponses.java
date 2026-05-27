package com.skala.backend.file.dto;

import java.time.Instant;
import java.util.List;

public final class ProjectFileResponses {

	private ProjectFileResponses() {
	}

	public record ProjectFileListResponse(Long projectId, List<ProjectFileResponse> items) {
	}

	public record ProjectFileResponse(
			Long fileId,
			String uploadedEvidenceTypeCode,
			String uploadedEvidenceTypeName,
			String originalFilename,
			String mimeType,
			Long sizeBytes,
			Instant capturedAt,
			Instant uploadedAt,
			String statusCode,
			long linkedItemCount
	) {
	}

	public record ProjectFileUploadResponse(
			Long fileId,
			String originalFilename,
			String uploadedEvidenceTypeCode,
			String mimeType,
			Long sizeBytes,
			Instant uploadedAt
	) {
	}
}
