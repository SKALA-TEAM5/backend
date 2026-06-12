package com.skala.backend.agent.service;

import com.skala.backend.agent.dto.UsageRecordResponses;
import com.skala.backend.agent.repository.AgentUsageRecordRepository;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.user.domain.RoleCode;
import org.springframework.http.HttpStatus;
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
        Long scopeUserId = resolveScopeUserId(currentUserId, roleCode);
        return repository.groupByUser(userId, projectId, agentTypeCode, toFrom(from), toTo(to), scopeUserId)
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
        Long scopeUserId = resolveScopeUserId(currentUserId, roleCode);
        return repository.groupByProject(userId, projectId, agentTypeCode, toFrom(from), toTo(to), scopeUserId)
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
        Long scopeUserId = resolveScopeUserId(currentUserId, roleCode);
        return repository.groupByAgent(userId, projectId, agentTypeCode, toFrom(from), toTo(to), scopeUserId)
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
        Long scopeUserId = resolveScopeUserId(currentUserId, roleCode);
        return repository.groupByMonth(userId, projectId, agentTypeCode, toFrom(from), toTo(to), scopeUserId)
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
        Long scopeUserId = resolveScopeUserId(currentUserId, roleCode);
        return repository.groupByDate(userId, projectId, agentTypeCode, toFrom(from), toTo(to), scopeUserId)
                .stream()
                .map(r -> new UsageRecordResponses.ByDate(
                        r.getDate(),
                        r.getInputTokens(), r.getOutputTokens(),
                        r.getCostUsd(), r.getCallCount()))
                .toList();
    }

    /**
     * 사용량 조회 권한 검증 후 프로젝트 집계 범위를 결정한다. (대시보드와 동일 정책)
     * - system_admin: 전체 집계 → null 반환(필터 미적용)
     * - admin, user: 본인 배정 프로젝트만 → 본인 userId 반환
     * - agent: 403
     */
    private Long resolveScopeUserId(Long currentUserId, RoleCode roleCode) {
        projectAccessService.requireCurrentUser(currentUserId);
        if (roleCode == RoleCode.AGENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }
        return roleCode == RoleCode.SYSTEM_ADMIN ? null : currentUserId;
    }

    private Instant toFrom(LocalDate date) {
        return date != null ? date.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }

    private Instant toTo(LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
    }
}
