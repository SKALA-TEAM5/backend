package com.skala.backend.file.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.file.dto.ProjectFileResponses.VisionDetections;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * files.detail JSONB의 vision_validation 결과를 응답 DTO로 변환한다.
 * 파일 목록 응답({@code ProjectFileService})과 세부내역 증빙 파일 응답({@code EvidenceQueryService})이 공유한다.
 * site_photo가 아니거나 vision agent 미실행이면 vision_validation 키가 없으므로 null을 반환한다.
 */
@Component
public class VisionDetectionParser {

	private final ObjectMapper objectMapper;

	public VisionDetectionParser(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public VisionDetections parse(String detail) {
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
