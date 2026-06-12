package com.skala.backend.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.file.dto.ProjectFileResponses.VisionDetections;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * files.detail JSONB의 vision_validation 결과를 응답 DTO로 변환한다.
 * 세부내역 증빙 파일 응답({@code EvidenceQueryService})에서만 사용한다.
 * 보호구 착용 사진(uploaded_evidence_type_code = wearing_photo)이 아니면 vision agent 결과가 있어도 null을 반환한다.
 */
@Component
public class VisionDetectionParser {

	private static final String WEARING_PHOTO = "wearing_photo";

	private final ObjectMapper objectMapper;

	public VisionDetectionParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	/**
	 * @param detail                    files.detail JSONB 원문
	 * @param uploadedEvidenceTypeCode  파일의 업로드 대표 버킷(D-04). wearing_photo일 때만 파싱한다.
	 */
	public VisionDetections parse(String detail, String uploadedEvidenceTypeCode) {
		if (!WEARING_PHOTO.equals(uploadedEvidenceTypeCode)) return null;
		if (detail == null) return null;
		try {
			JsonNode vision = objectMapper.readTree(detail).path("vision_validation");
			if (vision.isMissingNode()) return null;

			List<VisionDetections.Detection> detections = new ArrayList<>();
			for (JsonNode d : vision.path("detections")) {
				List<Double> bbox = new ArrayList<>();
				for (JsonNode coord : d.path("bbox_xyxy")) {
					bbox.add(coord.asDouble());
				}
				detections.add(new VisionDetections.Detection(
						d.path("label").asText(null),
						d.path("box_color").asText(null),
						d.path("confidence").asDouble(),
						d.path("is_wearing").asBoolean(),
						bbox
				));
			}
			return new VisionDetections(
					vision.path("image_width").asInt(),
					vision.path("image_height").asInt(),
					detections
			);
		} catch (Exception e) {
			return null;
		}
	}
}
