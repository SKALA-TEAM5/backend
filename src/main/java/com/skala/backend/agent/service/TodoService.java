package com.skala.backend.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.dto.AgentResponses;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.agent.repository.TodoRepository;
import com.skala.backend.agent.support.TodoKeyGenerator;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.domain.UsageStatementStatus;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * TODO 읽기 모델(todos 테이블) 운용.
 *
 * <ul>
 *   <li>{@link #refresh} — agent 실행 직후 호출되어 agent_logs.details JSONB를 평탄화해 statement 단위로 재생성한다.</li>
 *   <li>{@link #getTodos} — 평탄화된 TODO 목록을 조회한다(테이블만 읽음).</li>
 *   <li>{@link #confirmTodo} — 확인(체크) 토글. todo_key 보존 덕에 재생성에도 확인 상태가 유지된다.</li>
 * </ul>
 */
@Service
public class TodoService {

    private final ProjectAccessService projectAccessService;
    private final AgentLogRepository agentLogRepository;
    private final TodoRepository todoRepository;
    private final UsageStatementRepository usageStatementRepository;
    private final ObjectMapper objectMapper;

    public TodoService(ProjectAccessService projectAccessService, AgentLogRepository agentLogRepository,
            TodoRepository todoRepository, UsageStatementRepository usageStatementRepository,
            ObjectMapper objectMapper) {
        this.projectAccessService = projectAccessService;
        this.agentLogRepository = agentLogRepository;
        this.todoRepository = todoRepository;
        this.usageStatementRepository = usageStatementRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AgentResponses.TodoResponse> getTodos(Long currentUserId, Long projectId, Long usageStatementId) {
        projectAccessService.requireReadable(currentUserId, projectId);

        List<AgentResponses.TodoResponse> todos = todoRepository.findByStatement(usageStatementId);

        boolean reviewCompleted = usageStatementRepository.findById(usageStatementId)
                .map(s -> UsageStatementStatus.REVIEW_COMPLETED.getCode().equals(s.getStatusCode()))
                .orElse(false);
        if (reviewCompleted) {
            return todos.stream().filter(t -> !"legal".equals(t.agentTypeCode())).toList();
        }
        return todos;
    }

    @Transactional
    public void confirmTodo(Long currentUserId, Long projectId, Long todoId, boolean confirmed) {
        Long ownerProjectId = todoRepository.findProjectIdByTodoId(todoId);
        if (ownerProjectId == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "TODO를 찾을 수 없습니다.");
        }
        if (!ownerProjectId.equals(projectId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "해당 프로젝트의 TODO가 아닙니다.");
        }
        projectAccessService.requireReadable(currentUserId, projectId);
        todoRepository.updateConfirmed(todoId, confirmed, currentUserId);
    }

    /**
     * 사용내역서 단위로 TODO를 재생성한다(merge). agent 실행 직후 호출된다.
     * todo_key 기준 UPSERT로 내용을 갱신하되 confirmed는 보존하고, 이번 결과에 없는 행은 삭제한다.
     */
    @Transactional
    public void refresh(Long usageStatementId) {
        List<AgentLogRepository.AgentTodoRow> rows = agentLogRepository.findTodoLogs(usageStatementId);

        Set<String> currentKeys = new LinkedHashSet<>();
        for (AgentLogRepository.AgentTodoRow row : rows) {
            String agentTypeCode = row.getAgentTypeCode();
            JsonNode todos = parseTodos(row.getDetails());

            if (todos.isEmpty()) {
                // 항목 단위 TODO가 없는 경우(예: 전체 실패) — agent 단위 사유를 단건 행으로 보존한다.
                upsertSynthetic(usageStatementId, agentTypeCode, row.getReason(), currentKeys);
                continue;
            }
            for (JsonNode node : todos) {
                upsertFromNode(usageStatementId, agentTypeCode, node, currentKeys);
            }
        }

        todoRepository.deleteByStatementExcludingKeys(usageStatementId, currentKeys);
    }

    private void upsertFromNode(Long usageStatementId, String agentTypeCode, JsonNode node, Set<String> currentKeys) {
        String reason = text(node, "reason");
        if (reason == null) return;

        Long itemId = node.path("usage_statement_item_id").isNull() ? null
                : (node.hasNonNull("usage_statement_item_id") ? node.path("usage_statement_item_id").longValue() : null);
        String categoryCode = text(node, "category_code");
        Long fileId = node.hasNonNull("file_id") ? node.path("file_id").longValue() : null;
        String itemName = text(node, "usage_statement_item_name");
        String categoryName = text(node, "category_name");

        // safety-doc 노드는 한 항목의 여러 증빙 누락을 evidence_type_codes[] 배열 + 합쳐진 reason 하나로 보낸다.
        // 보완 작업(증빙 유형)마다 별도 TODO가 되어야 하므로 코드별로 분할한다.
        // reason을 코드 단위 문장으로 바꾸면 todo_key(=...|reason)가 갈라져 행/confirmed가 독립된다.
        JsonNode codes = node.path("evidence_type_codes");
        if (codes.isArray() && codes.size() > 1) {
            String title = text(node, "title"); // FastAPI: ", ".join(missing_evidences) — 합쳐진 표시문구
            for (JsonNode codeNode : codes) {
                String code = codeNode.isNull() ? null : codeNode.asText(null);
                if (code == null || code.isBlank()) continue;
                String perReason = (title != null && reason.contains(title)) ? reason.replace(title, code) : code;
                upsertOne(usageStatementId, agentTypeCode, itemId, itemName, categoryCode, categoryName,
                        fileId, perReason, code, currentKeys);
            }
            return;
        }

        // 단일 증빙 유형이면 그 코드를 구조화 컬럼으로 보존한다(조회 시 한글 표시명으로 변환).
        String evidenceTypeCode = singleEvidenceTypeCode(codes);
        upsertOne(usageStatementId, agentTypeCode, itemId, itemName, categoryCode, categoryName,
                fileId, reason, evidenceTypeCode, currentKeys);
    }

    private static String singleEvidenceTypeCode(JsonNode codes) {
        if (!codes.isArray() || codes.size() != 1) return null;
        JsonNode codeNode = codes.get(0);
        if (codeNode == null || codeNode.isNull()) return null;
        String code = codeNode.asText(null);
        return (code == null || code.isBlank()) ? null : code;
    }

    private void upsertOne(Long usageStatementId, String agentTypeCode, Long itemId, String itemName,
            String categoryCode, String categoryName, Long fileId, String reason, String evidenceTypeCode,
            Set<String> currentKeys) {
        String todoKey = TodoKeyGenerator.generate(usageStatementId, agentTypeCode, itemId, categoryCode, reason);
        if (!currentKeys.add(todoKey)) return; // 배치 내 중복 방지(UNIQUE 키)
        todoRepository.upsert(todoKey, usageStatementId, itemId, itemName, categoryCode, categoryName,
                agentTypeCode, fileId, reason, evidenceTypeCode);
    }

    private void upsertSynthetic(Long usageStatementId, String agentTypeCode, String reason, Set<String> currentKeys) {
        if (reason == null || reason.isBlank()) return;
        String todoKey = TodoKeyGenerator.generate(usageStatementId, agentTypeCode, null, null, reason);
        if (!currentKeys.add(todoKey)) return;
        todoRepository.upsert(todoKey, usageStatementId, null, null, null, null, agentTypeCode, null, reason, null);
    }

    private JsonNode parseTodos(String detailsJson) {
        if (detailsJson == null) return objectMapper.createArrayNode();
        try {
            JsonNode todos = objectMapper.readTree(detailsJson).path("payload").path("todos");
            return todos.isArray() ? todos : objectMapper.createArrayNode();
        } catch (Exception e) {
            return objectMapper.createArrayNode();
        }
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).isNull() ? null : node.path(field).asText(null);
    }
}
