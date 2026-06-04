package com.skala.backend.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.domain.AgentLog;
import com.skala.backend.agent.domain.AgentTypeCode;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatementStatus;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.StreamSupport;

@Service
public class AgentLogService {

    private static final List<String> VALIDATE_TYPES = List.of("safety-doc", "link", "vision");

    private final ProjectAccessService projectAccessService;
    private final AgentLogRepository agentLogRepository;
    private final UsageStatementRepository usageStatementRepository;
    private final ObjectMapper objectMapper;

    public AgentLogService(ProjectAccessService projectAccessService, AgentLogRepository agentLogRepository,
            UsageStatementRepository usageStatementRepository, ObjectMapper objectMapper) {
        this.projectAccessService = projectAccessService;
        this.agentLogRepository = agentLogRepository;
        this.usageStatementRepository = usageStatementRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.LogResponse> getLogs(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        List<AgentLog> logs = usageStatementId == null
                ? agentLogRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                : agentLogRepository.findByProjectIdAndUsageStatementIdOrderByCreatedAtDesc(projectId, usageStatementId);
        return logs.stream().map(AgentResponses.LogResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.WarningResponse> getWarnings(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        return agentLogRepository.findWarnings(projectId, usageStatementId)
                .stream()
                .map(AgentResponses.WarningResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgentResponses.ReportDetailResponse getReportDetail(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);
        AgentLog log = agentLogRepository
                .findTopByProjectIdAndUsageStatementIdAndAgentTypeCodeOrderByCreatedAtDesc(
                        projectId, usageStatementId, AgentTypeCode.REPORT)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "보고서 데이터가 없습니다."));
        return AgentResponses.ReportDetailResponse.from(log);
    }

    @Transactional(readOnly = true)
    public AgentResponses.TodoListResponse getTodos(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);

        List<AgentLogRepository.AgentTodoRow> rows = agentLogRepository.findTodoLogs(usageStatementId);

        List<AgentResponses.AgentTodoEntry> validate = rows.stream()
                .filter(r -> VALIDATE_TYPES.contains(r.getAgentTypeCode()))
                .map(this::toTodoEntry)
                .toList();

        boolean isReviewCompleted = usageStatementRepository.findById(usageStatementId)
                .map(s -> UsageStatementStatus.REVIEW_COMPLETED.getCode().equals(s.getStatusCode()))
                .orElse(false);

        AgentResponses.AgentTodoEntry legal = isReviewCompleted ? null : rows.stream()
                .filter(r -> "legal".equals(r.getAgentTypeCode()))
                .findFirst()
                .map(this::toTodoEntry)
                .orElse(null);

        return new AgentResponses.TodoListResponse(validate, legal);
    }

    private AgentResponses.AgentTodoEntry toTodoEntry(AgentLogRepository.AgentTodoRow row) {
        return new AgentResponses.AgentTodoEntry(
                row.getAgentTypeCode(),
                row.getResultCode(),
                row.getReason(),
                parseTodos(row.getDetails())
        );
    }

    private List<AgentResponses.TodoItem> parseTodos(String detailsJson) {
        if (detailsJson == null) return List.of();
        try {
            JsonNode todos = objectMapper.readTree(detailsJson).path("payload").path("todos");
            if (!todos.isArray()) return List.of();
            return StreamSupport.stream(todos.spliterator(), false)
                    .map(node -> new AgentResponses.TodoItem(
                            node.path("usage_statement_item_id").isNull() ? null
                                    : node.path("usage_statement_item_id").longValue(),
                            node.path("reason").asText(null)
                    ))
                    .filter(item -> item.reason() != null)
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
