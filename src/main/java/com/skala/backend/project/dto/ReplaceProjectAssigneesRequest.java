package com.skala.backend.project.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceProjectAssigneesRequest(
		@NotNull
		List<Long> assigneeUserIds
) {
}
