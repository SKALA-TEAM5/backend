package com.skala.backend.agent.client;

import com.skala.backend.agent.dto.AgentDtos.AgentType;
import com.skala.backend.agent.dto.AgentDtos.FastApiAgentRequest;
import com.skala.backend.agent.dto.AgentDtos.FastApiAgentResponse;
import com.skala.backend.global.error.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class FastApiAgentClient {

	private final RestClient restClient;
	private static final ParameterizedTypeReference<Map<String, Object>> MAP_RESPONSE_TYPE = new ParameterizedTypeReference<>() {
	};

	public FastApiAgentClient(
			RestClient.Builder builder,
			@Value("${app.fastapi.base-url:http://localhost:8001}") String baseUrl
	) {
		this.restClient = builder.baseUrl(baseUrl).build();
	}

	public FastApiAgentResponse run(AgentType agentType, FastApiAgentRequest request) {
		try {
			FastApiAgentResponse response = restClient.post()
					.uri("/api/v1/agents/{agentType}/run", agentType.code())
					.body(request)
					.retrieve()
					.body(FastApiAgentResponse.class);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI agent 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI agent 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI agent 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}

	// OCR parse API는 문서 유형별로 응답 shape이 달라서 Map으로 받습니다.
	// Spring은 원본 응답을 validation_logs.details에 보존하고, 필요한 필드만 후속 단계에 매핑합니다.
	public Map<String, Object> parseOcr(Map<String, Object> fileRecord) {
		try {
			Map<String, Object> response = restClient.post()
					.uri("/api/v1/ocr/parse")
					.body(fileRecord)
					.retrieve()
					.body(MAP_RESPONSE_TYPE);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI OCR 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI OCR 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI OCR 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}

	// matching/run은 OCR 결과와 사용내역서 항목을 비교합니다.
	// save_to_db=false로 호출하고, 저장은 Spring의 validation_logs 정책으로 일원화합니다.
	public Map<String, Object> runMatching(Map<String, Object> request) {
		try {
			Map<String, Object> response = restClient.post()
					.uri("/api/v1/matching/run")
					.body(request)
					.retrieve()
					.body(MAP_RESPONSE_TYPE);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI OCR 매칭 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI OCR 매칭 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI OCR 매칭 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}

	// law.yaml의 classification contract를 호출합니다.
	// 현재 사용내역서 업로드 플로우에서는 OCR 결과를 먼저 DB에 적재해 statementId를 만든 뒤 호출합니다.
	public Map<String, Object> runClassification(Long projectId, Long usageStatementId, Map<String, Object> request) {
		try {
			Map<String, Object> response = restClient.post()
					.uri("/projects/{projectId}/usage-statements/{statementId}/classification", projectId, usageStatementId)
					.body(request)
					.retrieve()
					.body(MAP_RESPONSE_TYPE);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI classification 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI classification 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI classification 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}

	public Map<String, Object> getClassificationStatus(Long projectId, Long usageStatementId, String classificationId) {
		return getMap("/projects/{projectId}/usage-statements/{statementId}/classification/{classificationId}", projectId, usageStatementId, classificationId);
	}

	public Map<String, Object> getLatestClassification(Long projectId, Long usageStatementId) {
		return getMap("/projects/{projectId}/usage-statements/{statementId}/classification/latest", projectId, usageStatementId);
	}

	public Map<String, Object> runValidation(Long projectId, Map<String, Object> request) {
		return postMap("/projects/{projectId}/validations", request, projectId);
	}

	public Map<String, Object> getValidationStatus(Long projectId, String validationId) {
		return getMap("/projects/{projectId}/validations/{validationId}", projectId, validationId);
	}

	public Map<String, Object> getLatestValidation(Long projectId) {
		return getMap("/projects/{projectId}/validations/latest", projectId);
	}

	public Map<String, Object> confirmValidation(Long projectId, String validationId, Map<String, Object> request) {
		return postMap("/projects/{projectId}/validations/{validationId}/confirm", request, projectId, validationId);
	}

	private Map<String, Object> getMap(String uri, Object... uriVariables) {
		try {
			Map<String, Object> response = restClient.get()
					.uri(uri, uriVariables)
					.retrieve()
					.body(MAP_RESPONSE_TYPE);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}

	private Map<String, Object> postMap(String uri, Map<String, Object> request, Object... uriVariables) {
		try {
			Map<String, Object> response = restClient.post()
					.uri(uri, uriVariables)
					.body(request)
					.retrieve()
					.body(MAP_RESPONSE_TYPE);
			if (response == null) {
				throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI 응답이 비어 있습니다.");
			}
			return response;
		} catch (ResourceAccessException exception) {
			throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "FastAPI 호출 시간이 초과되었거나 연결할 수 없습니다.");
		} catch (RestClientResponseException exception) {
			throw new ApiException(HttpStatus.BAD_GATEWAY, "FastAPI 호출에 실패했습니다. status=" + exception.getStatusCode().value());
		}
	}
}
