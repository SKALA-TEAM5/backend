package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.UsageRecordResponses;
import com.skala.backend.agent.repository.AgentUsageRecordRepository;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.user.domain.RoleCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AgentUsageRecordService {

    private final AgentUsageRecordRepository repository;
    private final ProjectAccessService projectAccessService;

    public AgentUsageRecordService(AgentUsageRecordRepository repository,
            ProjectAccessService projectAccessService) {
        this.repository = repository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public List<UsageRecordResponses.ByUser> getByUser(
            Long currentUserId, RoleCode roleCode,
            Long userId, Long projectId, String agentTypeCode,
            LocalDate from, LocalDate to) {
        projectAccessService.requireCurrentUser(currentUserId);
        Long effectiveUserId = isPrivileged(roleCode) ? userId : currentUserId;
        return repository.groupByUser(effectiveUserId, projectId, agentTypeCode, toFrom(from), toTo(to))
                .stream()
                .map(r -> new UsageRecordResponses.ByUser(
                        r.getUserId(), r.getUserName(),
                        r.getInputTokens(), r.getOutputTokens(),
                        r.getCostUsd(), r.getCallCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsageRecordResponses.ByProject> getByProject(
            Long currentUserId, RoleCode roleCode,
            Long userId, Long projectId, String agentTypeCode,
            LocalDate from, LocalDate to) {
        projectAccessService.requireCurrentUser(currentUserId);
        Long effectiveUserId = isPrivileged(roleCode) ? userId : currentUserId;
        return repository.groupByProject(effectiveUserId, projectId, agentTypeCode, toFrom(from), toTo(to))
                .stream()
                .map(r -> new UsageRecordResponses.ByProject(
                        r.getProjectId(), r.getProjectName(),
                        r.getInputTokens(), r.getOutputTokens(),
                        r.getCostUsd(), r.getCallCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsageRecordResponses.ByAgent> getByAgent(
            Long currentUserId, RoleCode roleCode,
            Long userId, Long projectId, String agentTypeCode,
            LocalDate from, LocalDate to) {
        projectAccessService.requireCurrentUser(currentUserId);
        Long effectiveUserId = isPrivileged(roleCode) ? userId : currentUserId;
        return repository.groupByAgent(effectiveUserId, projectId, agentTypeCode, toFrom(from), toTo(to))
                .stream()
                .map(r -> new UsageRecordResponses.ByAgent(
                        r.getAgentTypeCode(),
                        r.getInputTokens(), r.getOutputTokens(),
                        r.getCostUsd(), r.getCallCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsageRecordResponses.ByMonth> getByMonth(
            Long currentUserId, RoleCode roleCode,
            Long userId, Long projectId, String agentTypeCode,
            LocalDate from, LocalDate to) {
        projectAccessService.requireCurrentUser(currentUserId);
        Long effectiveUserId = isPrivileged(roleCode) ? userId : currentUserId;
        return repository.groupByMonth(effectiveUserId, projectId, agentTypeCode, toFrom(from), toTo(to))
                .stream()
                .map(r -> new UsageRecordResponses.ByMonth(
                        r.getMonth(),
                        r.getInputTokens(), r.getOutputTokens(),
                        r.getCostUsd(), r.getCallCount()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<UsageRecordResponses.ByDate> getByDate(
            Long currentUserId, RoleCode roleCode,
            Long userId, Long projectId, String agentTypeCode,
            LocalDate from, LocalDate to) {
        projectAccessService.requireCurrentUser(currentUserId);
        Long effectiveUserId = isPrivileged(roleCode) ? userId : currentUserId;
        return repository.groupByDate(effectiveUserId, projectId, agentTypeCode, toFrom(from), toTo(to))
                .stream()
                .map(r -> new UsageRecordResponses.ByDate(
                        r.getDate(),
                        r.getInputTokens(), r.getOutputTokens(),
                        r.getCostUsd(), r.getCallCount()))
                .toList();
    }

    private boolean isPrivileged(RoleCode roleCode) {
        return roleCode == RoleCode.SYSTEM_ADMIN || roleCode == RoleCode.ADMIN;
    }

    private Instant toFrom(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    private Instant toTo(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }
}
