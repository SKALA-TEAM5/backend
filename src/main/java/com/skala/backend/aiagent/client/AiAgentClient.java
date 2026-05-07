package com.skala.backend.aiagent.client;

public interface AiAgentClient {

	AiAgentClientDtos.AiAgentClientResponse run(AiAgentClientDtos.AiAgentClientRequest request);
}
