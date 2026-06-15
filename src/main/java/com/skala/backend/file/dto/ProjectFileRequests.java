package com.skala.backend.file.dto;

public final class ProjectFileRequests {

	private ProjectFileRequests() {
	}

	public record UpdateFileNameRequest(String originalFilename) {
	}
}
