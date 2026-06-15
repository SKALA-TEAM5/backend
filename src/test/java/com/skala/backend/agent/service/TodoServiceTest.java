package com.skala.backend.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skala.backend.agent.repository.AgentLogRepository;
import com.skala.backend.agent.repository.TodoRepository;
import com.skala.backend.agent.support.TodoKeyGenerator;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.usage.repository.UsageStatementRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TodoService#refresh} 의 평탄화/분할 동작 검증.
 *
 * <p>핵심은 safety-doc 노드의 {@code evidence_type_codes[]} 가 여러 개면 보완 작업(증빙 유형)마다
 * 별도 TODO(=별도 todo_key)로 분할되는지다(이슈: 여러 보완 작업이 단일 todoId로 묶이던 버그).
 */
@ExtendWith(MockitoExtension.class)
class TodoServiceTest {

    private static final Long STATEMENT_ID = 42L;

    @Mock ProjectAccessService projectAccessService;
    @Mock AgentLogRepository agentLogRepository;
    @Mock TodoRepository todoRepository;
    @Mock UsageStatementRepository usageStatementRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TodoService service() {
        return new TodoService(projectAccessService, agentLogRepository, todoRepository,
                usageStatementRepository, objectMapper);
    }

    private static AgentLogRepository.AgentTodoRow row(String agentTypeCode, String reason, String details) {
        return new AgentLogRepository.AgentTodoRow() {
            @Override public String getAgentTypeCode() { return agentTypeCode; }
            @Override public String getStatusCode() { return "success"; }
            @Override public String getResultCode() { return "hil"; }
            @Override public String getReason() { return reason; }
            @Override public String getDetails() { return details; }
        };
    }

    @Test
    void safetyDoc_evidence_type_codes_여러개면_코드별_분할_upsert() {
        String details = """
                {"payload":{"todos":[
                  {"usage_statement_item_id":101,"category_code":"CAT_03","category_name":"안전시설비",
                   "usage_statement_item_name":"OO항목",
                   "title":"전자세금계산서, 보호구착용사진",
                   "evidence_type_codes":["전자세금계산서","보호구착용사진"],
                   "reason":"OO항목 필수 증빙 누락: 전자세금계산서, 보호구착용사진"}
                ]}}
                """;
        when(agentLogRepository.findTodoLogs(STATEMENT_ID))
                .thenReturn(List.of(row("safety-doc", "필수 증빙 누락", details)));

        service().refresh(STATEMENT_ID);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(todoRepository, org.mockito.Mockito.times(2)).upsert(
                keyCaptor.capture(), eq(STATEMENT_ID), eq(101L), eq("OO항목"),
                eq("CAT_03"), eq("안전시설비"), eq("safety-doc"), any(), reasonCaptor.capture());

        // 코드별로 reason 이 분리된다
        assertThat(reasonCaptor.getAllValues()).containsExactlyInAnyOrder(
                "OO항목 필수 증빙 누락: 전자세금계산서",
                "OO항목 필수 증빙 누락: 보호구착용사진");
        // 두 TODO 는 서로 다른 todo_key 를 가진다 → confirmed 독립
        assertThat(keyCaptor.getAllValues()).doesNotHaveDuplicates();
    }

    @Test
    void safetyDoc_evidence_type_code_하나면_원래_reason_그대로_단일_upsert() {
        String reason = "OO항목 필수 증빙 누락: 전자세금계산서";
        String details = """
                {"payload":{"todos":[
                  {"usage_statement_item_id":101,"category_code":"CAT_03","category_name":"안전시설비",
                   "usage_statement_item_name":"OO항목",
                   "title":"전자세금계산서",
                   "evidence_type_codes":["전자세금계산서"],
                   "reason":"OO항목 필수 증빙 누락: 전자세금계산서"}
                ]}}
                """;
        when(agentLogRepository.findTodoLogs(STATEMENT_ID))
                .thenReturn(List.of(row("safety-doc", "필수 증빙 누락", details)));

        service().refresh(STATEMENT_ID);

        String expectedKey = TodoKeyGenerator.generate(STATEMENT_ID, "safety-doc", 101L, "CAT_03", reason);
        verify(todoRepository).upsert(eq(expectedKey), eq(STATEMENT_ID), eq(101L), eq("OO항목"),
                eq("CAT_03"), eq("안전시설비"), eq("safety-doc"), any(), eq(reason));
    }

    @Test
    void evidence_type_codes_없는_노드는_기존동작_유지_단일_upsert() {
        // link 노드: evidence_type_codes 없음 → 분할 없이 reason 그대로
        String reason = "OO항목 증빙 매칭 검토 필요: review_needed";
        String details = """
                {"payload":{"todos":[
                  {"usage_statement_item_id":77,"category_code":"CAT_05","category_name":"보호구",
                   "usage_statement_item_name":"OO항목","file_id":55,"title":"영수증/세금계산서",
                   "reason":"OO항목 증빙 매칭 검토 필요: review_needed"}
                ]}}
                """;
        when(agentLogRepository.findTodoLogs(STATEMENT_ID))
                .thenReturn(List.of(row("link", "매칭 검토 필요", details)));

        service().refresh(STATEMENT_ID);

        String expectedKey = TodoKeyGenerator.generate(STATEMENT_ID, "link", 77L, "CAT_05", reason);
        verify(todoRepository).upsert(eq(expectedKey), eq(STATEMENT_ID), eq(77L), eq("OO항목"),
                eq("CAT_05"), eq("보호구"), eq("link"), eq(55L), eq(reason));
    }

    @Test
    void todos_없는_실패로그는_agent_단위_synthetic_행_보존() {
        String details = """
                {"payload":{"todos":[]}}
                """;
        when(agentLogRepository.findTodoLogs(STATEMENT_ID))
                .thenReturn(List.of(row("vision", "현장사진 다운로드 실패", details)));

        service().refresh(STATEMENT_ID);

        String expectedKey = TodoKeyGenerator.generate(STATEMENT_ID, "vision", null, null, "현장사진 다운로드 실패");
        verify(todoRepository).upsert(eq(expectedKey), eq(STATEMENT_ID), eq(null), eq(null),
                eq(null), eq(null), eq("vision"), eq(null), eq("현장사진 다운로드 실패"));
    }
}
