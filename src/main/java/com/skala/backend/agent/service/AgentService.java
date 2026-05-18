package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.AgentDtos.AgentRunRequest;
import com.skala.backend.agent.dto.AgentDtos.AgentRunResponse;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

	// FastAPI 엔드포인트 확정 후 구현 예정
	public AgentRunResponse run(AuthenticatedUser currentUser, Long projectId, String agentType, AgentRunRequest request) {
		throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "FastAPI 연동 구현 예정입니다.");
	}
}
