package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.dto.AgentRequests;
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

	public void parse(Long currentUserId, Long projectId, AgentRequests.ParseRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		fastApiAgentClient.parseUsageStatement(request.fileId());
	}

	public void classify(Long currentUserId, Long projectId, AgentRequests.ClassifyRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		fastApiAgentClient.classifyItem(
				projectId,
				request.usageStatementId(),
				request.itemName(),
				request.usedOn(),
				request.unit(),
				request.quantity(),
				request.unitPrice(),
				request.totalAmount()
		);
	}

	public void validate(Long currentUserId, Long projectId, AgentRequests.ValidateRequest request) {
		projectAccessService.requireReadable(currentUserId, projectId);
		fastApiAgentClient.runValidation(projectId, request.usageStatementId());
	}
}
