package com.skala.backend.admin.dto;

import java.math.BigDecimal;
import java.util.List;

public class AdminDashboardResponses {

    public record DashboardSummary(int totalProjects, int reviewNeededProjects) {}

    public record AiUsageTotal(
            long totalInputTokens,
            long totalOutputTokens,
            long callCount,
            BigDecimal totalCostUsd) {}

    public record AiUsageByAgent(
            String agentTypeCode,
            long inputTokens,
            long outputTokens,
            long callCount,
            BigDecimal costUsd) {}

    public record AiUsageByUser(
            Long userId,
            String userName,
            String roleCode,
            long inputTokens,
            long outputTokens,
            long callCount,
            BigDecimal costUsd) {}

    public record AiUsageByProject(
            Long projectId,
            String projectName,
            String type,
            long inputTokens,
            long outputTokens,
            long callCount,
            BigDecimal costUsd) {}

    public record AiUsageSummary(
            AiUsageTotal total,
            List<AiUsageByAgent> byAgent,
            List<AiUsageByUser> topUsers,
            List<AiUsageByProject> topProjects) {}

    public record SupplementAssignee(Long userId, String userName, int supplementCount) {}

    public record DashboardResponse(
            DashboardSummary summary,
            AiUsageSummary aiUsage,
            List<SupplementAssignee> supplementAssignees) {}

    public record ProjectListItem(
            Long id,
            String projectName,
            String contractNo,
            String statusCode,
            String constructionStartDate,
            String constructionEndDate,
            BigDecimal progressRate,
            BigDecimal usageRate,
            String assignees) {}

    public record ProjectListResponse(long totalCount, List<ProjectListItem> items) {}
}
