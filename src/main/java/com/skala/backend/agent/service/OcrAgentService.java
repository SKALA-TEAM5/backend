package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.OcrAgentDtos.OcrEvidenceMatchRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrUsageStatementParseRequest;
import com.skala.backend.agent.dto.OcrAgentDtos.OcrWorkflowResponse;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class OcrAgentService {

	// FastAPI 엔드포인트 확정 후 구현 예정
	public OcrWorkflowResponse parseUsageStatement(AuthenticatedUser currentUser, Long projectId, OcrUsageStatementParseRequest request) {
		throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "FastAPI 연동 구현 예정입니다.");
	}

	// FastAPI 엔드포인트 확정 후 구현 예정
	public OcrWorkflowResponse parseAndMatchEvidence(AuthenticatedUser currentUser, Long projectId, OcrEvidenceMatchRequest request) {
		throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "FastAPI 연동 구현 예정입니다.");
	}
}
