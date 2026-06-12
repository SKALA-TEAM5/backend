package com.skala.backend.file.dto;

import io.swagger.v3.oas.annotations.media.Schema;

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

	@Schema(description = "파일 업로드 응답. vision 처리 전이므로 바운딩 박스 데이터 미포함.")
	public record ProjectFileUploadResponse(
			Long fileId,
			String originalFilename,
			String uploadedEvidenceTypeCode,
			String mimeType,
			Long sizeBytes,
			Instant uploadedAt
	) {
	}

	public record UploadAndLinkResponse(Long fileId, Long linkId) {
	}

	@Schema(description = "wearing_photo 파일의 vision agent 바운딩 박스 결과. vision 미실행 또는 비wearing_photo이면 null.")
	public record VisionDetections(
			@Schema(description = "원본 이미지 너비 (px)", example = "677")
			int imageWidth,
			@Schema(description = "원본 이미지 높이 (px)", example = "493")
			int imageHeight,
			@Schema(description = "감지된 객체 목록")
			List<Detection> detections
	) {
		@Schema(description = "감지된 개별 객체")
		public record Detection(
				@Schema(description = "감지 라벨", example = "안전모 착용")
				String label,
				@Schema(description = "박스 색상", example = "blue")
				String boxColor,
				@Schema(description = "신뢰도 (0~1)", example = "0.8332")
				double confidence,
				@Schema(description = "착용 여부", example = "true")
				boolean isWearing,
				@Schema(description = "바운딩 박스 좌표 [x1, y1, x2, y2]", example = "[241.49, 33.61, 431.7, 278.23]")
				List<Double> bboxXyxy
		) {
		}
	}
}
