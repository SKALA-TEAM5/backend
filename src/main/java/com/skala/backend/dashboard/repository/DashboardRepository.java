package com.skala.backend.dashboard.repository;

import com.skala.backend.dashboard.dto.DashboardResponses;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Repository
public class DashboardRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DashboardRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── 상단 요약 ─────────────────────────────────────────────────────────────

    public DashboardResponses.Summary getSummary() {
        String sql = """
                SELECT
                    COUNT(DISTINCT p.id)                                                AS total_projects,
                    COUNT(DISTINCT CASE WHEN us.status_code = 'upload_completed'
                                        THEN p.id END)                                  AS review_required_count
                FROM service.projects p
                LEFT JOIN service.usage_statements us ON us.project_id = p.id
                """;
        return jdbc.queryForObject(sql, Map.of(), (rs, n) -> new DashboardResponses.Summary(
                rs.getLong("total_projects"),
                rs.getLong("review_required_count")
        ));
    }

    // ── AI 사용량 ─────────────────────────────────────────────────────────────

    public DashboardResponses.AiUsageTotal getAiUsageTotal(Instant from, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildAiWhere(from, to, params);
        String sql = """
                SELECT
                    COALESCE(SUM(input_tokens) + SUM(output_tokens), 0) AS total_tokens,
                    COUNT(*)                                              AS total_calls,
                    COALESCE(SUM(cost_usd), 0)                           AS total_cost_usd
                FROM service.agent_usage_records r
                """ + where;
        return jdbc.queryForObject(sql, params, (rs, n) -> new DashboardResponses.AiUsageTotal(
                rs.getLong("total_tokens"),
                rs.getLong("total_calls"),
                rs.getBigDecimal("total_cost_usd")
        ));
    }

    public List<DashboardResponses.AiUsageByUser> getAiUsageByUser(Instant from, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildAiWhere(from, to, params);
        String sql = """
                SELECT
                    u.id                                                          AS user_id,
                    u.real_name                                                   AS user_name,
                    u.role_code,
                    COALESCE(SUM(r.input_tokens) + SUM(r.output_tokens), 0)       AS total_tokens,
                    COALESCE(SUM(r.cost_usd), 0)                                  AS cost_usd,
                    COUNT(*)                                                      AS call_count
                FROM service.agent_usage_records r
                JOIN service.users u ON u.id = r.user_id
                """ + where + """

                GROUP BY u.id, u.real_name, u.role_code
                ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
                LIMIT 5
                """;
        return jdbc.query(sql, params, (rs, n) -> new DashboardResponses.AiUsageByUser(
                rs.getLong("user_id"),
                rs.getString("user_name"),
                rs.getString("role_code"),
                rs.getLong("total_tokens"),
                rs.getBigDecimal("cost_usd"),
                rs.getLong("call_count")
        ));
    }

    public List<DashboardResponses.AiUsageByProject> getAiUsageByProject(Instant from, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String where = buildAiWhere(from, to, params);
        String sql = """
                SELECT
                    p.id                                                          AS project_id,
                    p.project_name,
                    COALESCE(SUM(r.input_tokens) + SUM(r.output_tokens), 0)       AS total_tokens,
                    COALESCE(SUM(r.cost_usd), 0)                                  AS cost_usd,
                    COUNT(*)                                                      AS call_count
                FROM service.agent_usage_records r
                JOIN service.projects p ON p.id = r.project_id
                """ + where + """

                GROUP BY p.id, p.project_name
                ORDER BY COALESCE(SUM(r.cost_usd), 0) DESC
                LIMIT 5
                """;
        return jdbc.query(sql, params, (rs, n) -> new DashboardResponses.AiUsageByProject(
                rs.getLong("project_id"),
                rs.getString("project_name"),
                "project",
                rs.getLong("total_tokens"),
                rs.getBigDecimal("cost_usd"),
                rs.getLong("call_count")
        ));
    }

    private String buildAiWhere(Instant from, Instant to, MapSqlParameterSource params) {
        StringBuilder where = new StringBuilder("WHERE TRUE");
        if (from != null) {
            where.append(" AND r.created_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND r.created_at < :to");
            params.addValue("to", Timestamp.from(to));
        }
        return where.toString();
    }

    // ── 담당자별 보완 진행 현황 ───────────────────────────────────────────────

    public List<DashboardResponses.SupplementProgress> getSupplementProgress() {
        String sql = """
                SELECT
                    u.id        AS user_id,
                    u.real_name AS user_name,
                    u.role_code,
                    COUNT(DISTINCT us.id) AS supplement_count
                FROM service.users u
                JOIN service.project_user_assignments pua ON pua.user_id = u.id
                JOIN service.usage_statements us         ON us.project_id = pua.project_id
                WHERE us.status_code = 'supplement_required'
                  AND us.report_month >= DATE_TRUNC('month', CURRENT_DATE)
                  AND us.report_month <  DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month'
                GROUP BY u.id, u.real_name, u.role_code
                ORDER BY supplement_count DESC
                LIMIT 3
                """;
        return jdbc.query(sql, Map.of(),
                (rs, n) -> new DashboardResponses.SupplementProgress(
                        rs.getLong("user_id"),
                        rs.getString("user_name"),
                        rs.getString("role_code"),
                        rs.getLong("supplement_count")
                ));
    }

}
