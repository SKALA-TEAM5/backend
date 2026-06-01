package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentRequests;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;

@Service
public class AgentService {

	private final FastApiAgentClient fastApiAgentClient;
	private final ProjectAccessService projectAccessService;

	public AgentService(FastApiAgentClient fastApiAgentClient, ProjectAccessService projectAccessService) {
		this.fastApiAgentClient = fastApiAgentClient;
		this.projectAccessService = projectAccessService;
	}

	public AgentResponses.ParseResult parse(Long currentUserId, Long projectId, AgentRequests.ParseRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		return fastApiAgentClient.parseUsageStatement(projectId, request.fileId());
	}

	public void validate(Long currentUserId, Long projectId, AgentRequests.ValidateRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		fastApiAgentClient.runValidation(projectId, request.usageStatementId(), currentUserId);
	}

	public void legal(Long currentUserId, Long projectId, AgentRequests.LegalRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		fastApiAgentClient.runLegal(projectId, request.usageStatementId(), currentUserId);
	}

	public void report(Long currentUserId, Long projectId, AgentRequests.ReportRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		fastApiAgentClient.runReport(projectId, request.usageStatementId(), currentUserId);
	}
}
