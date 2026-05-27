package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

	// FastAPI 엔드포인트 확정 후 구현 예정
	public AgentResponses.RunResponse run(AuthenticatedUser currentUser, Long projectId, String agentType, AgentRequests.RunRequest request) {
		throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "FastAPI 연동 구현 예정입니다.");
	}
}
