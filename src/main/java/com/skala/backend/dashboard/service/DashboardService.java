package com.skala.backend.dashboard.service;

import com.skala.backend.dashboard.dto.DashboardResponses;
import com.skala.backend.dashboard.repository.DashboardRepository;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DashboardService {

    private final DashboardRepository repository;
    private final ProjectAccessService projectAccessService;

    public DashboardService(DashboardRepository repository, ProjectAccessService projectAccessService) {
        this.repository = repository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public DashboardResponses.Summary getSummary(Long currentUserId) {
        projectAccessService.requireAdmin(currentUserId);
        return repository.getSummary();
    }

    @Transactional(readOnly = true)
    public DashboardResponses.AiUsage getAiUsage(Long currentUserId, Integer year, Integer month) {
        projectAccessService.requireAdmin(currentUserId);

        Instant from = null;
        Instant to = null;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            from = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            to   = ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } else if (year != null) {
            from = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            to   = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        DashboardResponses.AiUsageTotal total = repository.getAiUsageTotal(from, to);
        List<DashboardResponses.AiUsageByUser> byUser = repository.getAiUsageByUser(from, to);
        List<DashboardResponses.AiUsageByProject> byProject = repository.getAiUsageByProject(from, to);

        return new DashboardResponses.AiUsage(total, byUser, byProject);
    }

    @Transactional(readOnly = true)
    public List<DashboardResponses.SupplementProgress> getSupplementProgress(Long currentUserId) {
        projectAccessService.requireAdmin(currentUserId);
        return repository.getSupplementProgress();
    }
}
