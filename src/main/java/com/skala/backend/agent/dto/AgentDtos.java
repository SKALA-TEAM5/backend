package com.skala.backend.agent.dto;

import com.skala.backend.global.error.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

public final class AgentDtos {

	private AgentDtos() {
	}

	public enum AgentType {
		VALIDATOR("validator", "law"),
		CLASSIFIER("classifier", "classification"),
		SAFETY_DOC("safety_doc", "evidence"),
		REPORT("report", "report");

		private final String code;
		private final String validationTypeCode;

		AgentType(String code, String validationTypeCode) {
			this.code = code;
			this.validationTypeCode = validationTypeCode;
		}

		public String code() {
			return code;
		}

		public String validationTypeCode() {
			return validationTypeCode;
		}

		public static AgentType from(String value) {
			for (AgentType type : values()) {
				if (type.code.equalsIgnoreCase(value) || type.name().equalsIgnoreCase(value)) {
					return type;
				}
			}
			throw new ApiException(HttpStatus.BAD_REQUEST, "지원하지 않는 agentType입니다.");
		}
	}

	@Schema(description = "Agent 실행 요청")
	public record AgentRunRequest(
			@Schema(description = "사용내역서 ID", example = "2008")
			@NotNull(message = "usageStatementId는 필수입니다.")
			Long usageStatementId,
			@Schema(description = "특정 항목만 실행할 때 사용하는 사용내역서 상세항목 ID", example = "10")
			Long usageStatementItemId,
			@Schema(description = "Agent별 선택 옵션")
			Map<String, Object> options
	) {
		public Map<String, Object> optionsOrEmpty() {
			return options == null ? Map.of() : options;
		}
	}

	@Schema(description = "Agent 실행 응답")
	public record AgentRunResponse(
			@Schema(description = "요청 추적 ID")
			String requestId,
			@Schema(description = "Agent 유형", example = "validator")
			String agentType,
			@Schema(description = "실행 상태", example = "succeeded")
			String status,
			@Schema(description = "저장된 validation_logs ID 목록")
			List<Long> validationLogIds,
			@Schema(description = "Agent 실행 결과")
			Map<String, Object> result
	) {
	}

	public record FastApiAgentRequest(
			String requestId,
			String agentType,
			String inputVersion,
			Map<String, Object> context,
			Map<String, Object> options
	) {
	}

	public record FastApiAgentResponse(
			String requestId,
			String agentType,
			String outputVersion,
			String status,
			Map<String, Object> result,
			AgentUsage usage,
			AgentError error
	) {
	}

	public record AgentUsage(
			String model,
			Integer inputTokens,
			Integer outputTokens
	) {
	}

	public record AgentError(
			String code,
			String message,
			Map<String, Object> details
	) {
	}

	public record ValidationLogCommand(
			Long projectId,
			Long usageStatementId,
			Long usageStatementItemId,
			String validationTypeCode,
			String resultCode,
			String detailsJson,
			String modelName,
			String agentTypeCode,
			String logTypeCode,
			String severityCode
	) {
	}
}
