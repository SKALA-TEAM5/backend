package com.skala.backend.admin.service;

import com.skala.backend.admin.dto.AdminDashboardResponses.*;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.project.service.ProjectAccessService;
import com.skala.backend.user.domain.RoleCode;
import com.skala.backend.user.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDashboardService {

    /**
     * 본인이 배정된 프로젝트로 한정하는 서브쿼리. 파라미터로 user_id 1개를 받는다.
     * system_admin은 이 필터를 적용하지 않고 전체 프로젝트를 집계한다.
     */
    private static final String PROJECT_SCOPE_FILTER =
            " IN (SELECT pua_scope.project_id FROM service.project_user_assignments pua_scope"
            + " WHERE pua_scope.user_id = ?)";

    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccessService;

    public AdminDashboardService(JdbcTemplate jdbc, ProjectAccessService projectAccessService) {
        this.jdbc = jdbc;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long currentUserId) {
        Long scopeUserId = resolveScopeUserId(currentUserId);
        return new DashboardResponse(querySummary(scopeUserId), querySupplementAssignees(scopeUserId));
    }

    @Transactional(readOnly = true)
    public AiUsageSummary getAiUsage(Long currentUserId, Integer year, Integer month) {
        Long scopeUserId = resolveScopeUserId(currentUserId);
        return queryAiUsage(scopeUserId, year, month);
    }

    /**
     * 대시보드 접근 권한 검증 후 프로젝트 집계 범위를 결정한다.
     * - system_admin: 전체 집계 → null 반환(필터 미적용)
     * - admin, user: 본인 배정 프로젝트만 → 본인 userId 반환
     * - agent: 403
     */
    private Long resolveScopeUserId(Long currentUserId) {
        User currentUser = projectAccessService.requireCurrentUser(currentUserId);
        if (currentUser.getRoleCode() == RoleCode.AGENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "권한이 없습니다.");
        }
        return currentUser.getRoleCode() == RoleCode.SYSTEM_ADMIN ? null : currentUser.getId();
    }

    private DashboardSummary querySummary(Long scopeUserId) {
        boolean scoped = scopeUserId != null;

        String totalSql = "SELECT COUNT(*) FROM service.projects p"
                + (scoped ? " WHERE p.id" + PROJECT_SCOPE_FILTER : "");
        Integer total = scoped
                ? jdbc.queryForObject(totalSql, Integer.class, scopeUserId)
                : jdbc.queryForObject(totalSql, Integer.class);

        String reviewSql = """
                SELECT COUNT(DISTINCT p.id)
                FROM service.projects p
                WHERE EXISTS (
                    SELECT 1 FROM service.usage_statements us
                    WHERE us.project_id = p.id
                      AND us.status_code IN ('upload_completed', 'supplement_required')
                )
                """
                + (scoped ? " AND p.id" + PROJECT_SCOPE_FILTER : "");
        Integer reviewNeeded = scoped
                ? jdbc.queryForObject(reviewSql, Integer.class, scopeUserId)
                : jdbc.queryForObject(reviewSql, Integer.class);

        return new DashboardSummary(
                total        != null ? total        : 0,
                reviewNeeded != null ? reviewNeeded : 0);
    }

    private AiUsageSummary queryAiUsage(Long scopeUserId, Integer year, Integer month) {
        Instant from = null;
        Instant to   = null;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            from = ym.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            to   = ym.atEndOfMonth().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        } else if (year != null) {
            from = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
            to   = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        }

        List<String> conditions = new ArrayList<>();
        List<Object> baseParams = new ArrayList<>();
        if (from != null) { conditions.add("r.created_at >= ?"); baseParams.add(Timestamp.from(from)); }
        if (to   != null) { conditions.add("r.created_at < ?");  baseParams.add(Timestamp.from(to)); }
        if (scopeUserId != null) { conditions.add("r.project_id" + PROJECT_SCOPE_FILTER); baseParams.add(scopeUserId); }
        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        List<Object> p = new ArrayList<>(baseParams);
        AiUsageTotal total = jdbc.queryForObject(
                "SELECT COALESCE(SUM(input_tokens) + SUM(output_tokens),0) AS total_tokens, COUNT(*) AS cc, COALESCE(SUM(cost_usd),0) AS tc"
                + " FROM service.agent_usage_records r" + where,
                (rs, row) -> new AiUsageTotal(
                        rs.getLong("total_tokens"),
                        rs.getLong("cc"), rs.getBigDecimal("tc")),
                p.toArray());

        List<Object> p2 = new ArrayList<>(baseParams);
        List<AiUsageByAgent> byAgent = jdbc.query(
                "SELECT agent_type_code,"
                + " COALESCE(SUM(input_tokens) + SUM(output_tokens),0) AS total_tokens,"
                + " COUNT(*) AS call_count, COALESCE(SUM(cost_usd),0) AS cost_usd"
                + " FROM service.agent_usage_records r" + where
                + " GROUP BY agent_type_code ORDER BY total_tokens DESC",
                (rs, row) -> new AiUsageByAgent(
                        rs.getString("agent_type_code"),
                        rs.getLong("total_tokens"),
                        rs.getLong("call_count"), rs.getBigDecimal("cost_usd")),
                p2.toArray());

        List<Object> p3 = new ArrayList<>(baseParams);
        List<AiUsageByUser> topUsers = jdbc.query(
                "SELECT u.id AS user_id, u.real_name AS user_name, u.role_code,"
                + " COALESCE(SUM(r.input_tokens) + SUM(r.output_tokens),0) AS total_tokens,"
                + " COUNT(*) AS call_count, COALESCE(SUM(r.cost_usd),0) AS cost_usd"
                + " FROM service.agent_usage_records r JOIN service.users u ON u.id = r.user_id" + where
                + " GROUP BY u.id, u.real_name, u.role_code ORDER BY total_tokens DESC LIMIT 8",
                (rs, row) -> new AiUsageByUser(
                        rs.getLong("user_id"), rs.getString("user_name"), rs.getString("role_code"),
                        rs.getLong("total_tokens"),
                        rs.getLong("call_count"), rs.getBigDecimal("cost_usd")),
                p3.toArray());

        List<Object> p4 = new ArrayList<>(baseParams);
        List<AiUsageByProject> topProjects = jdbc.query(
                "SELECT p.id AS project_id, p.project_name,"
                + " COALESCE(SUM(r.input_tokens) + SUM(r.output_tokens),0) AS total_tokens,"
                + " COUNT(*) AS call_count, COALESCE(SUM(r.cost_usd),0) AS cost_usd"
                + " FROM service.agent_usage_records r JOIN service.projects p ON p.id = r.project_id" + where
                + " GROUP BY p.id, p.project_name ORDER BY total_tokens DESC LIMIT 8",
                (rs, row) -> new AiUsageByProject(
                        rs.getLong("project_id"), rs.getString("project_name"), "project",
                        rs.getLong("total_tokens"),
                        rs.getLong("call_count"), rs.getBigDecimal("cost_usd")),
                p4.toArray());

        return new AiUsageSummary(total, byAgent, topUsers, topProjects);
    }

    private List<SupplementAssignee> querySupplementAssignees(Long scopeUserId) {
        boolean scoped = scopeUserId != null;
        String sql = """
                SELECT
                    u.id                            AS user_id,
                    u.real_name                     AS user_name,
                    u.role_code,
                    COUNT(DISTINCT us.id)::int       AS supplement_count
                FROM service.project_user_assignments pua
                JOIN service.users u ON u.id = pua.user_id
                JOIN service.usage_statements us ON us.project_id = pua.project_id
                WHERE us.status_code = 'supplement_required'
                  AND us.report_month = date_trunc('month', CURRENT_DATE)::date
                """
                + (scoped ? " AND us.project_id" + PROJECT_SCOPE_FILTER : "")
                + """
                  GROUP BY u.id, u.real_name, u.role_code
                  ORDER BY supplement_count DESC
                  LIMIT 3
                """;
        org.springframework.jdbc.core.RowMapper<SupplementAssignee> mapper = (rs, row) -> new SupplementAssignee(
                rs.getLong("user_id"),
                rs.getString("user_name"),
                rs.getString("role_code"),
                rs.getInt("supplement_count"));
        return scoped
                ? jdbc.query(sql, mapper, scopeUserId)
                : jdbc.query(sql, mapper);
    }
}
