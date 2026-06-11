package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.domain.AgentTypeCode;
import com.skala.backend.agent.repository.AgentLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AgentAsyncService {

	private static final Logger log = LoggerFactory.getLogger(AgentAsyncService.class);

	private final FastApiAgentClient fastApiAgentClient;
	private final AgentLogRepository agentLogRepository;
	private final TodoService todoService;

	public AgentAsyncService(FastApiAgentClient fastApiAgentClient, AgentLogRepository agentLogRepository,
			TodoService todoService) {
		this.fastApiAgentClient = fastApiAgentClient;
		this.agentLogRepository = agentLogRepository;
		this.todoService = todoService;
	}

	@Async("agentAsyncExecutor")
	public void fireValidate(Long projectId, Long usageStatementId, Long userId) {
		try {
			fastApiAgentClient.runValidation(projectId, usageStatementId, userId);
		} catch (Exception e) {
			log.warn("validate FastAPI 호출 실패 — fail 보정 (statementId={}): {}", usageStatementId, e.getMessage());
			upsertFail(projectId, usageStatementId, AgentTypeCode.SAFETY_DOC);
			upsertFail(projectId, usageStatementId, AgentTypeCode.LINK);
			upsertFail(projectId, usageStatementId, AgentTypeCode.VISION);
		} finally {
			refreshTodos(usageStatementId);
		}
	}

	@Async("agentAsyncExecutor")
	public void fireLegal(Long projectId, Long usageStatementId, Long userId) {
		try {
			fastApiAgentClient.runLegal(projectId, usageStatementId, userId);
		} catch (Exception e) {
			log.warn("legal FastAPI 호출 실패 — fail 보정 (statementId={}): {}", usageStatementId, e.getMessage());
			upsertFail(projectId, usageStatementId, AgentTypeCode.LEGAL);
		} finally {
			refreshTodos(usageStatementId);
		}
	}

	@Async("agentAsyncExecutor")
	public void fireReport(Long projectId, Long usageStatementId, Long userId) {
		try {
			fastApiAgentClient.runReport(projectId, usageStatementId, userId);
		} catch (Exception e) {
			log.warn("report FastAPI 호출 실패 — fail 보정 (statementId={}): {}", usageStatementId, e.getMessage());
			upsertFail(projectId, usageStatementId, AgentTypeCode.REPORT);
		}
	}

	/** agent 실행 직후 TODO 읽기 모델을 재생성한다. 실패해도 agent 흐름을 막지 않는다. */
	private void refreshTodos(Long usageStatementId) {
		try {
			todoService.refresh(usageStatementId);
		} catch (Exception ex) {
			log.error("todos 재생성 실패 (statementId={}): {}", usageStatementId, ex.getMessage());
		}
	}

	private void upsertFail(Long projectId, Long usageStatementId, AgentTypeCode agentTypeCode) {
		try {
			agentLogRepository.upsertStatementLogFail(projectId, usageStatementId, agentTypeCode.getCode());
		} catch (Exception ex) {
			log.error("fail 보정 UPSERT 실패 (statementId={}, type={}): {}", usageStatementId, agentTypeCode, ex.getMessage());
		}
	}
}
