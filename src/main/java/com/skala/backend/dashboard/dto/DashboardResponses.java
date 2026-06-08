package com.skala.backend.dashboard.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

public final class DashboardResponses {

    private DashboardResponses() {}

    public record Summary(
            long totalProjects,
            long reviewRequiredCount
    ) {}

    @Schema(name = "DashboardAiUsageTotal")
    public record AiUsageTotal(
            long totalTokens,
            long totalCalls,
            BigDecimal totalCostUsd
    ) {}

    @Schema(name = "DashboardAiUsageByUser")
    public record AiUsageByUser(
            Long userId,
            String userName,
            String roleCode,
            long totalTokens,
            BigDecimal costUsd,
            long callCount
    ) {}

    @Schema(name = "DashboardAiUsageByProject")
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
