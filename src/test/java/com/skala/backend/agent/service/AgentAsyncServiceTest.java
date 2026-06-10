package com.skala.backend.agent.service;

import com.skala.backend.agent.client.FastApiAgentClient;
import com.skala.backend.agent.repository.AgentLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class AgentAsyncServiceTest {

    @Mock FastApiAgentClient fastApiAgentClient;
    @Mock AgentLogRepository agentLogRepository;

    @InjectMocks
    AgentAsyncService agentAsyncService;

    // ─── fireValidate ─────────────────────────────────────────────────────

    @Test
    void fireValidate_성공_시_fail_보정_호출_안_함() {
        agentAsyncService.fireValidate(1L, 2L, 3L);

        verify(agentLogRepository, never()).upsertStatementLogFail(anyLong(), anyLong(), anyString());
    }

    @Test
    void fireValidate_FastAPI_예외_시_safety_doc_link_vision_fail_보정() {
        doThrow(new RestClientException("connection refused"))
                .when(fastApiAgentClient).runValidation(anyLong(), anyLong(), anyLong());

        agentAsyncService.fireValidate(1L, 2L, 3L);

        verify(agentLogRepository).upsertStatementLogFail(1L, 2L, "safety-doc");
        verify(agentLogRepository).upsertStatementLogFail(1L, 2L, "link");
        verify(agentLogRepository).upsertStatementLogFail(1L, 2L, "vision");
        verifyNoMoreInteractions(agentLogRepository);
    }

    @Test
    void fireValidate_upsert_실패해도_예외_전파_안_함() {
        doThrow(new RestClientException("down")).when(fastApiAgentClient).runValidation(anyLong(), anyLong(), anyLong());
        doThrow(new RuntimeException("db error")).when(agentLogRepository).upsertStatementLogFail(anyLong(), anyLong(), anyString());

        // 예외가 전파되지 않아야 한다
        agentAsyncService.fireValidate(1L, 2L, 3L);
    }

    // ─── fireLegal ────────────────────────────────────────────────────────

    @Test
    void fireLegal_성공_시_fail_보정_호출_안_함() {
        agentAsyncService.fireLegal(1L, 2L, 3L);

        verify(agentLogRepository, never()).upsertStatementLogFail(anyLong(), anyLong(), anyString());
    }

    @Test
    void fireLegal_FastAPI_예외_시_legal_fail_보정() {
        doThrow(new RestClientException("connection refused"))
                .when(fastApiAgentClient).runLegal(anyLong(), anyLong(), anyLong());

        agentAsyncService.fireLegal(1L, 2L, 3L);

        verify(agentLogRepository).upsertStatementLogFail(1L, 2L, "legal");
        verifyNoMoreInteractions(agentLogRepository);
    }

    @Test
    void fireLegal_upsert_실패해도_예외_전파_안_함() {
        doThrow(new RestClientException("down")).when(fastApiAgentClient).runLegal(anyLong(), anyLong(), anyLong());
        doThrow(new RuntimeException("db error")).when(agentLogRepository).upsertStatementLogFail(anyLong(), anyLong(), anyString());

        agentAsyncService.fireLegal(1L, 2L, 3L);
    }

    // ─── fireReport ───────────────────────────────────────────────────────

    @Test
    void fireReport_성공_시_fail_보정_호출_안_함() {
        agentAsyncService.fireReport(1L, 2L, 3L);

        verify(agentLogRepository, never()).upsertStatementLogFail(anyLong(), anyLong(), anyString());
    }

    @Test
    void fireReport_FastAPI_예외_시_report_fail_보정() {
        doThrow(new RestClientException("connection refused"))
                .when(fastApiAgentClient).runReport(anyLong(), anyLong(), anyLong());

        agentAsyncService.fireReport(1L, 2L, 3L);

        verify(agentLogRepository).upsertStatementLogFail(1L, 2L, "report");
        verifyNoMoreInteractions(agentLogRepository);
    }

    @Test
    void fireReport_upsert_실패해도_예외_전파_안_함() {
        doThrow(new RestClientException("down")).when(fastApiAgentClient).runReport(anyLong(), anyLong(), anyLong());
        doThrow(new RuntimeException("db error")).when(agentLogRepository).upsertStatementLogFail(anyLong(), anyLong(), anyString());

        agentAsyncService.fireReport(1L, 2L, 3L);
    }
}
