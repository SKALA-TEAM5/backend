package com.skala.backend.aiagent.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientResult;
import com.skala.backend.aiagent.domain.AiAgentRun;
import com.skala.backend.aiagent.domain.ValidationLog;
import com.skala.backend.aiagent.repository.ValidationLogRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ValidationLogWriter {

	private final ValidationLogRepository validationLogRepository;
	private final ObjectMapper objectMapper;

	public ValidationLogWriter(ValidationLogRepository validationLogRepository, ObjectMapper objectMapper) {
		this.validationLogRepository = validationLogRepository;
		this.objectMapper = objectMapper;
	}

	public int writeResults(AiAgentRun run, List<AiAgentClientResult> results) {
		if (results == null || results.isEmpty()) {
			return 0;
		}
		List<ValidationLog> logs = results.stream()
				.map(result -> ValidationLog.create(
						run.getProjectId(),
						run.getId(),
						result.usageStatementId(),
						result.usageStatementItemId(),
						run.getAgentTypeCode(),
						valueOrDefault(result.validationTypeCode(), run.getAgentTypeCode().getValue()),
						valueOrDefault(result.logTypeCode(), "agent_result"),
						result.severityCode(),
						result.resultCode(),
						toJson(result.details()),
						result.modelName()
				))
				.toList();
		return validationLogRepository.saveAll(logs).size();
	}

	public void writeError(AiAgentRun run, String message) {
		ObjectNode details = objectMapper.createObjectNode();
		details.put("message", message);
		validationLogRepository.save(ValidationLog.create(
				run.getProjectId(),
				run.getId(),
				null,
				null,
				run.getAgentTypeCode(),
				run.getAgentTypeCode().getValue(),
				"agent_error",
				"error",
				"fail",
				toJson(details),
				null
		));
	}

	private String valueOrDefault(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value;
	}

	private String toJson(JsonNode details) {
		try {
			return details == null ? "{}" : objectMapper.writeValueAsString(details);
		} catch (JsonProcessingException exception) {
			return "{}";
		}
	}
}
