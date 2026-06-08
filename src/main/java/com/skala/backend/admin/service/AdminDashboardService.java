package com.skala.backend.admin.service;

import com.skala.backend.admin.dto.AdminDashboardResponses.*;
import com.skala.backend.project.service.ProjectAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminDashboardService {

    private static final Map<String, String> SORT_COLUMN_MAP = Map.of(
            "projectName",  "p.project_name",
            "contractNo",   "p.contract_no",
            "progressRate", "progress_rate",
            "usageRate",    "usage_rate",
            "startDate",    "p.construction_start_date",
            "endDate",      "p.construction_end_date",
            "assignees",    "assignees"
    );
    private static final Set<String> VALID_SORT_DIRS = Set.of("asc", "desc");
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final ProjectAccessService projectAccessService;

    public AdminDashboardService(JdbcTemplate jdbc, ProjectAccessService projectAccessService) {
        this.jdbc = jdbc;
        this.projectAccessService = projectAccessService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(Long currentUserId) {
        projectAccessService.requireAdmin(currentUserId);
        return new DashboardResponse(querySummary(), queryAiUsage(), querySupplementAssignees());
    }

    @Transactional(readOnly = true)
    public ProjectListResponse getProjectList(
            Long currentUserId,
            String keyword,
            String assigneeName,
            String statusCode,
            String sortBy,
            String sortDir,
            int page,
            int size) {
        projectAccessService.requireAdmin(currentUserId);

        String sortColumn = SORT_COLUMN_MAP.getOrDefault(sortBy, "p.project_name");
        String direction   = VALID_SORT_DIRS.contains(sortDir != null ? sortDir.toLowerCase() : "") ? sortDir.toLowerCase() : "asc";
        int    clampedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int    offset      = (Math.max(page, 1) - 1) * clampedSize;

        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("WHERE 1=1");

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (p.project_name ILIKE ? OR p.contract_no ILIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (assigneeName != null && !assigneeName.isBlank()) {
            where.append(
                    " AND EXISTS ("
                    + " SELECT 1 FROM service.project_user_assignments a2"
                    + " JOIN service.users u2 ON u2.id = a2.user_id"
                    + " WHERE a2.project_id = p.id AND u2.real_name ILIKE ?"
                    + ")");
            params.add("%" + assigneeName.trim() + "%");
        }
        if (statusCode != null && !statusCode.isBlank()) {
            where.append(" AND p.project_status_code = ?");
            params.add(statusCode.trim());
        }

        List<Object> countParams = new ArrayList<>(params);
        Long totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM service.projects p " + where,
                Long.class, countParams.toArray());

        if (totalCount == null || totalCount == 0) {
            return new ProjectListResponse(0, List.of());
        }

        String sql = """
                SELECT
                    p.id,
                    p.project_name,
                    p.contract_no,
                    p.project_status_code,
                    p.construction_start_date::text,
                    p.construction_end_date::text,
                    COALESCE(STRING_AGG(DISTINCT u.real_name, ', ' ORDER BY u.real_name), '') AS assignees,
                    COALESCE(latest.progress_rate, 0)                                          AS progress_rate,
                    CASE WHEN p.appropriated_amount > 0
                         THEN ROUND(COALESCE(latest.cumulative_total, 0) * 100.0 / p.appropriated_amount, 2)
                         ELSE 0 END                                                            AS usage_rate
                FROM service.projects p
                LEFT JOIN service.project_user_assignments pua ON pua.project_id = p.id
                LEFT JOIN service.users u ON u.id = pua.user_id
                LEFT JOIN LATERAL (
                    SELECT
                        us.cumulative_progress_rate                  AS progress_rate,
                        COALESCE(SUM(uss.cumulative_amount), 0)      AS cumulative_total
                    FROM service.usage_statements us
                    LEFT JOIN service.usage_statement_summaries uss ON uss.usage_statement_id = us.id
                    WHERE us.project_id = p.id
                    GROUP BY us.id, us.cumulative_progress_rate
                    ORDER BY us.report_month DESC
                    LIMIT 1
                ) latest ON true
                """
                + "\n" + where + "\n"
                + "GROUP BY p.id, p.project_name, p.contract_no, p.project_status_code,"
                + " p.construction_start_date, p.construction_end_date, p.appropriated_amount,"
                + " latest.progress_rate, latest.cumulative_total\n"
                + "ORDER BY " + sortColumn + " " + direction
                + "\nLIMIT ? OFFSET ?";

        params.add(clampedSize);
        params.add(offset);

        List<ProjectListItem> items = jdbc.query(sql, (rs, row) -> new ProjectListItem(
                rs.getLong("id"),
                rs.getString("project_name"),
                rs.getString("contract_no"),
                rs.getString("project_status_code"),
                rs.getString("construction_start_date"),
                rs.getString("construction_end_date"),
                rs.getBigDecimal("progress_rate"),
                rs.getBigDecimal("usage_rate"),
                rs.getString("assignees")
        ), params.toArray());

        return new ProjectListResponse(totalCount, items);
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
                      AND us.status_code IN ('upload_completed', 'supplement_required')
                      AND us.report_month = (
                          SELECT MAX(us2.report_month)
                          FROM service.usage_statements us2
                          WHERE us2.project_id = p.id
                      )
                )
                """, Integer.class);

        return new DashboardSummary(
                total        != null ? total        : 0,
                reviewNeeded != null ? reviewNeeded : 0);
    }

    private AiUsageSummary queryAiUsage() {
        AiUsageTotal total = jdbc.queryForObject("""
                SELECT
                    COALESCE(SUM(input_tokens), 0)  AS total_input,
                    COALESCE(SUM(output_tokens), 0) AS total_output,
                    COUNT(*)                         AS call_count,
                    COALESCE(SUM(cost_usd), 0)       AS total_cost
                FROM service.agent_usage_records
                """,
                (rs, row) -> new AiUsageTotal(
                        rs.getLong("total_input"),
                        rs.getLong("total_output"),
                        rs.getLong("call_count"),
                        rs.getBigDecimal("total_cost")));

        List<AiUsageByAgent> byAgent = jdbc.query("""
                SELECT
                    agent_type_code,
                    COALESCE(SUM(input_tokens), 0)  AS input_tokens,
                    COALESCE(SUM(output_tokens), 0) AS output_tokens,
                    COUNT(*)                         AS call_count,
                    COALESCE(SUM(cost_usd), 0)       AS cost_usd
                FROM service.agent_usage_records
                GROUP BY agent_type_code
                ORDER BY COALESCE(SUM(cost_usd), 0) DESC
                """,
                (rs, row) -> new AiUsageByAgent(
                        rs.getString("agent_type_code"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getLong("call_count"),
                        rs.getBigDecimal("cost_usd")));

        List<AiUsageByUser> topUsers = jdbc.query("""
                SELECT
                    u.id                                        AS user_id,
                    u.real_name                                 AS user_name,
                    u.role_code                                 AS role_code,
                    COALESCE(SUM(r.input_tokens), 0)            AS input_tokens,
                    COALESCE(SUM(r.output_tokens), 0)           AS output_tokens,
                    COUNT(*)                                    AS call_count,
                    COALESCE(SUM(r.cost_usd), 0)                AS cost_usd
                FROM service.agent_usage_records r
                JOIN service.users u ON u.id = r.user_id
                GROUP BY u.id, u.real_name, u.role_code
                ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
                LIMIT 5
                """,
                (rs, row) -> new AiUsageByUser(
                        rs.getLong("user_id"),
                        rs.getString("user_name"),
                        rs.getString("role_code"),
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getLong("call_count"),
                        rs.getBigDecimal("cost_usd")));

        List<AiUsageByProject> topProjects = jdbc.query("""
                SELECT
                    p.id                                        AS project_id,
                    p.project_name                              AS project_name,
                    COALESCE(SUM(r.input_tokens), 0)            AS input_tokens,
                    COALESCE(SUM(r.output_tokens), 0)           AS output_tokens,
                    COUNT(*)                                    AS call_count,
                    COALESCE(SUM(r.cost_usd), 0)                AS cost_usd
                FROM service.agent_usage_records r
                JOIN service.projects p ON p.id = r.project_id
                GROUP BY p.id, p.project_name
                ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
                LIMIT 5
                """,
                (rs, row) -> new AiUsageByProject(
                        rs.getLong("project_id"),
                        rs.getString("project_name"),
                        "project",
                        rs.getLong("input_tokens"),
                        rs.getLong("output_tokens"),
                        rs.getLong("call_count"),
                        rs.getBigDecimal("cost_usd")));

        return new AiUsageSummary(total, byAgent, topUsers, topProjects);
    }

    private List<SupplementAssignee> querySupplementAssignees() {
        return jdbc.query("""
                SELECT
                    u.id                            AS user_id,
                    u.real_name                     AS user_name,
                    COUNT(DISTINCT us.id)::int       AS supplement_count
                FROM service.project_user_assignments pua
                JOIN service.users u ON u.id = pua.user_id
                JOIN service.usage_statements us ON us.project_id = pua.project_id
                WHERE us.status_code = 'supplement_required'
                  AND us.report_month = date_trunc('month', CURRENT_DATE)::date
                GROUP BY u.id, u.real_name
                ORDER BY supplement_count DESC
                LIMIT 3
                """,
                (rs, row) -> new SupplementAssignee(
                        rs.getLong("user_id"),
                        rs.getString("user_name"),
                        rs.getInt("supplement_count")));
    }
}
