package com.skala.backend.aiagent.repository;

import com.skala.backend.aiagent.domain.AiAgentRun;
import com.skala.backend.aiagent.domain.AiAgentRunStatusCode;
import com.skala.backend.aiagent.domain.AiAgentTypeCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface AiAgentRunRepository extends JpaRepository<AiAgentRun, Long> {

	boolean existsByProjectIdAndAgentTypeCodeAndStatusCodeIn(
			Long projectId,
			AiAgentTypeCode agentTypeCode,
			Collection<AiAgentRunStatusCode> statusCodes
	);
}
