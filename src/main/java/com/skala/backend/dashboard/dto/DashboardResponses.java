package com.skala.backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardResponses {

    private DashboardResponses() {}

    public record Summary(
            long totalProjects,
            long reviewRequiredCount
    ) {}

    public record ProjectRow(
            Long projectId,
            String projectName,
            String contractNo,
            String statusCode,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal progressRate,
            BigDecimal usageRate,
            List<String> managers
    ) {}

    public record ProjectList(
            long total,
            List<ProjectRow> items
    ) {}

    public record AiUsageTotal(
            long totalTokens,
            long totalCalls,
            BigDecimal totalCostUsd
    ) {}

    public record AiUsageByUser(
            Long userId,
            String userName,
            String roleCode,
            long totalTokens,
            BigDecimal costUsd,
            long callCount
    ) {}

    public record AiUsageByProject(
            Long projectId,
            String projectName,
            String type,
            long totalTokens,
            BigDecimal costUsd,
            long callCount
    ) {}

    public record AiUsage(
            AiUsageTotal total,
            List<AiUsageByUser> byUser,
            List<AiUsageByProject> byProject
    ) {}

    public record SupplementProgress(
            Long userId,
            String userName,
            String roleCode,
            long supplementCount
    ) {}
}
