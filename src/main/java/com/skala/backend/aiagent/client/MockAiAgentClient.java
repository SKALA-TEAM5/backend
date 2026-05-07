package com.skala.backend.aiagent.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AgentFileContext;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientRequest;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientResponse;
import com.skala.backend.aiagent.client.AiAgentClientDtos.AiAgentClientResult;
import com.skala.backend.aiagent.domain.AiAgentRunStatusCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Profile("mock-aiagent")
public class MockAiAgentClient implements AiAgentClient {

	private final ObjectMapper objectMapper;

	public MockAiAgentClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public AiAgentClientResponse run(AiAgentClientRequest request) {
		List<AiAgentClientResult> results = new ArrayList<>();
		for (AgentFileContext file : files(request)) {
			results.add(new AiAgentClientResult(
					file.fileId(),
					null,
					null,
					validationTypeCode(request),
					"agent_mock_result",
					"info",
					"pass",
					details(request, file),
					"mock-ai-agent"
			));
		}
		if (results.isEmpty()) {
			results.add(new AiAgentClientResult(
					null,
					null,
					null,
					validationTypeCode(request),
					"agent_mock_result",
					"info",
					"pass",
					details(request, null),
					"mock-ai-agent"
			));
		}
		return new AiAgentClientResponse(
				request.aiAgentRunId(),
				request.agentTypeCode(),
				AiAgentRunStatusCode.COMPLETED,
				null,
				results
		);
	}

	@SuppressWarnings("unchecked")
	private List<AgentFileContext> files(AiAgentClientRequest request) {
		Object value = request.context().get("files");
		if (value instanceof List<?> files) {
			return (List<AgentFileContext>) files;
		}
		return List.of();
	}

	private String validationTypeCode(AiAgentClientRequest request) {
		return switch (request.agentTypeCode()) {
			case OCR_AGENT -> "ocr";
			case CLASSIFIER_AGENT -> "classification";
			case VISION_AGENT -> "vision";
			case SAFELEE_AGENT -> "safelee";
			case VALIDATOR_AGENT -> "validation";
			case REPORT_AGENT -> "report";
		};
	}

	private ObjectNode details(AiAgentClientRequest request, AgentFileContext file) {
		ObjectNode details = objectMapper.createObjectNode();
		details.put("summary", "mock agent 처리 완료");
		details.put("agentTypeCode", request.agentTypeCode().getValue());
		if (file != null) {
			details.put("fileId", file.fileId());
			details.put("originalFilename", file.originalFilename());
			details.put("storageKey", file.storageKey());
			details.put("mimeType", file.mimeType());
		}
		return details;
	}
}
