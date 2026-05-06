package com.skala.backend.evidence.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class EvidenceRequests {

	private EvidenceRequests() {
	}

	public record LinkEvidenceFileRequest(
			@NotNull Long fileId,
			@NotBlank String evidenceTypeCode
	) {
	}

	public record MoveEvidenceFileLinkRequest(
			@NotNull Long targetItemId,
			@NotBlank String evidenceTypeCode
	) {
	}
}
