package com.skala.backend.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public class AdminDashboardResponses {

    public record DashboardSummary(int totalProjects, int reviewNeededProjects) {}

    @Schema(name = "AdminAiUsageTotal")
    public record AiUsageTotal(
            long totalTokens,
            long callCount,
            BigDecimal totalCostUsd) {}

    @Schema(name = "AdminAiUsageByAgent")
    public record AiUsageByAgent(
            String agentTypeCode,
            long totalTokens,
            long callCount,
            BigDecimal costUsd) {}

    @Schema(name = "AdminAiUsageByUser")
    public record AiUsageByUser(
            Long userId,
            String userName,
            String roleCode,
            long totalTokens,
            long callCount,
            BigDecimal costUsd) {}

    @Schema(name = "AdminAiUsageByProject")
    public record AiUsageByProject(
            Long projectId,
            String projectName,
            String type,
            long totalTokens,
            long callCount,
            BigDecimal costUsd) {}

    public record AiUsageSummary(
            AiUsageTotal total,
            List<AiUsageByAgent> byAgent,
            List<AiUsageByUser> topUsers,
            List<AiUsageByProject> topProjects) {}

    public record SupplementAssignee(Long userId, String userName, String roleCode, int supplementCount) {}

    public record DashboardResponse(
            DashboardSummary summary,
            List<SupplementAssignee> supplementAssignees) {}

}
