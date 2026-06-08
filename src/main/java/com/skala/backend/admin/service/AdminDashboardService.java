package com.skala.backend.admin.service;

import com.skala.backend.admin.dto.AdminDashboardResponses.*;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminDashboardService {

    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccessService;

    public AdminDashboardService(JdbcTemplate jdbc, ProjectAccessService projectAccessService) {
        this.jdbc = jdbc;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long currentUserId) {
        projectAccessService.requireAdmin(currentUserId);
        return new DashboardResponse(querySummary(), querySupplementAssignees());
    }

    @Transactional(readOnly = true)
    public AiUsageSummary getAiUsage(Long currentUserId, Integer year, Integer month) {
        projectAccessService.requireAdmin(currentUserId);
        return queryAiUsage(year, month);
    }

    private DashboardSummary querySummary() {
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service.projects",
                Integer.class);

        Integer reviewNeeded = jdbc.queryForObject("""
                SELECT COUNT(DISTINCT p.id)
                FROM service.projects p
                WHERE EXISTS (
                    SELECT 1 FROM service.usage_statements us
                    WHERE us.project_id = p.id
                      AND us.status_code = 'upload_completed'
                )
                """, Integer.class);

        return new DashboardSummary(
                total        != null ? total        : 0,
                reviewNeeded != null ? reviewNeeded : 0);
    }

    private AiUsageSummary queryAiUsage(Integer year, Integer month) {
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

        String where = buildWhere(from, to);
        List<Object> baseParams = buildParams(from, to);

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
                p2.toArray(),
                (rs, row) -> new AiUsageByAgent(
                        rs.getString("agent_type_code"),
                        rs.getLong("total_tokens"),
                        rs.getLong("call_count"), rs.getBigDecimal("cost_usd")));

        List<Object> p3 = new ArrayList<>(baseParams);
        List<AiUsageByUser> topUsers = jdbc.query(
                "SELECT u.id AS user_id, u.real_name AS user_name, u.role_code,"
                + " COALESCE(SUM(r.input_tokens) + SUM(r.output_tokens),0) AS total_tokens,"
                + " COUNT(*) AS call_count, COALESCE(SUM(r.cost_usd),0) AS cost_usd"
                + " FROM service.agent_usage_records r JOIN service.users u ON u.id = r.user_id" + where
                + " GROUP BY u.id, u.real_name, u.role_code ORDER BY total_tokens DESC LIMIT 8",
                p3.toArray(),
                (rs, row) -> new AiUsageByUser(
                        rs.getLong("user_id"), rs.getString("user_name"), rs.getString("role_code"),
                        rs.getLong("total_tokens"),
                        rs.getLong("call_count"), rs.getBigDecimal("cost_usd")));

        List<Object> p4 = new ArrayList<>(baseParams);
        List<AiUsageByProject> topProjects = jdbc.query(
                "SELECT p.id AS project_id, p.project_name,"
                + " COALESCE(SUM(r.input_tokens) + SUM(r.output_tokens),0) AS total_tokens,"
                + " COUNT(*) AS call_count, COALESCE(SUM(r.cost_usd),0) AS cost_usd"
                + " FROM service.agent_usage_records r JOIN service.projects p ON p.id = r.project_id" + where
                + " GROUP BY p.id, p.project_name ORDER BY total_tokens DESC LIMIT 8",
                p4.toArray(),
                (rs, row) -> new AiUsageByProject(
                        rs.getLong("project_id"), rs.getString("project_name"), "project",
                        rs.getLong("total_tokens"),
                        rs.getLong("call_count"), rs.getBigDecimal("cost_usd")));

        return new AiUsageSummary(total, byAgent, topUsers, topProjects);
    }

    private String buildWhere(Instant from, Instant to) {
        if (from != null && to != null) return " WHERE r.created_at >= ? AND r.created_at < ?";
        if (from != null)              return " WHERE r.created_at >= ?";
        if (to   != null)              return " WHERE r.created_at < ?";
        return "";
    }

    private List<Object> buildParams(Instant from, Instant to) {
        List<Object> params = new ArrayList<>();
        if (from != null) params.add(Timestamp.from(from));
        if (to   != null) params.add(Timestamp.from(to));
        return params;
    }

    private List<SupplementAssignee> querySupplementAssignees() {
        return jdbc.query("""
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
                GROUP BY u.id, u.real_name, u.role_code
                ORDER BY supplement_count DESC
                LIMIT 3
                """,
                (rs, row) -> new SupplementAssignee(
                        rs.getLong("user_id"),
                        rs.getString("user_name"),
                        rs.getString("role_code"),
                        rs.getInt("supplement_count")));
    }
}
