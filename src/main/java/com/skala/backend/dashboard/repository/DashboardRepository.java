package com.skala.backend.dashboard.repository;

import com.skala.backend.dashboard.dto.DashboardResponses;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class DashboardRepository {

    private static final Set<String> ALLOWED_SORT_COLS = Set.of(
            "project_name", "contract_no", "progress_rate", "usage_rate", "start_date", "end_date", "assignee"
    );
    private static final Map<String, String> SORT_COL_SQL = Map.of(
            "project_name",  "p.project_name",
            "contract_no",   "COALESCE(p.contract_no, '')",
            "progress_rate", "progress_rate",
            "usage_rate",    "usage_rate",
            "start_date",    "p.construction_start_date",
            "end_date",      "p.construction_end_date",
            "assignee",      "assignee_names"
    );

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

    // ── 프로젝트 리스트 ───────────────────────────────────────────────────────

    public DashboardResponses.ProjectList getProjects(
            String keyword, String statusCode, Long managerId,
            LocalDate periodFrom, LocalDate periodTo,
            String sortCol, String sortDir,
            int page, int size
    ) {
        String orderBy = buildOrderBy(sortCol, sortDir);
        String ctes = projectCtes();
        String where = buildProjectWhere();

        String dataSql = ctes + """
                SELECT
                    p.id,
                    p.project_name,
                    p.contract_no,
                    p.project_status_code,
                    p.construction_start_date,
                    p.construction_end_date,
                    COALESCE(ls.cumulative_progress_rate, 0)                        AS progress_rate,
                    CASE WHEN p.appropriated_amount > 0
                         THEN ROUND(COALESCE(su.total_spent, 0)
                              / p.appropriated_amount * 100, 2)
                         ELSE 0 END                                                 AS usage_rate,
                    COALESCE((
                        SELECT string_agg(u.real_name, '|' ORDER BY pua.id)
                        FROM service.project_user_assignments pua
                        JOIN service.users u ON u.id = pua.user_id
                        WHERE pua.project_id = p.id
                    ), '')                                                           AS assignee_names
                FROM service.projects p
                LEFT JOIN latest_statement ls  ON ls.project_id        = p.id
                LEFT JOIN statement_usage  su  ON su.usage_statement_id = ls.statement_id
                """ + where + "\nORDER BY " + orderBy + "\nLIMIT :size OFFSET :offset";

        String countSql = """
                SELECT COUNT(*)
                FROM service.projects p
                LEFT JOIN service.project_user_assignments pua_filter
                    ON pua_filter.project_id = p.id AND (:managerId)::bigint IS NOT NULL
                """ + where.replace("latest_statement", "x").replace("ls.", "p.") // count doesn't need CTEs
                // use simpler count query
                ;
        // Simpler count without CTEs
        countSql = "SELECT COUNT(*) FROM service.projects p " + buildProjectCountWhere();

        MapSqlParameterSource params = buildProjectParams(keyword, statusCode, managerId, periodFrom, periodTo);
        params.addValue("size", size);
        params.addValue("offset", (page - 1) * size);

        List<DashboardResponses.ProjectRow> items = jdbc.query(dataSql, params, (rs, n) -> {
            String names = rs.getString("assignee_names");
            List<String> managers = (names == null || names.isBlank())
                    ? List.of()
                    : Arrays.stream(names.split("\\|")).filter(s -> !s.isBlank()).toList();
            return new DashboardResponses.ProjectRow(
                    rs.getLong("id"),
                    rs.getString("project_name"),
                    rs.getString("contract_no"),
                    rs.getString("project_status_code"),
                    toLocalDate(rs.getDate("construction_start_date")),
                    toLocalDate(rs.getDate("construction_end_date")),
                    rs.getBigDecimal("progress_rate"),
                    rs.getBigDecimal("usage_rate"),
                    managers
            );
        });

        Long total = jdbc.queryForObject(countSql, params, Long.class);
        return new DashboardResponses.ProjectList(total != null ? total : 0L, items);
    }

    private String projectCtes() {
        return """
                WITH latest_statement AS (
                    SELECT DISTINCT ON (us.project_id)
                        us.project_id,
                        us.id AS statement_id,
                        us.cumulative_progress_rate
                    FROM service.usage_statements us
                    ORDER BY us.project_id, us.report_month DESC, us.revision_no DESC
                ),
                statement_usage AS (
                    SELECT uss.usage_statement_id,
                           COALESCE(SUM(uss.cumulative_amount), 0) AS total_spent
                    FROM service.usage_statement_summaries uss
                    GROUP BY uss.usage_statement_id
                )
                """;
    }

    private String buildProjectWhere() {
        return """
                WHERE (CAST(:keyword AS text) IS NULL OR (
                    LOWER(p.project_name) LIKE :keyword
                    OR LOWER(COALESCE(p.contract_no, '')) LIKE :keyword))
                  AND (CAST(:statusCode AS text) IS NULL OR p.project_status_code = :statusCode)
                  AND (CAST(:periodFrom AS date) IS NULL OR p.construction_end_date   >= :periodFrom)
                  AND (CAST(:periodTo   AS date) IS NULL OR p.construction_start_date <= :periodTo)
                  AND (CAST(:managerId AS bigint) IS NULL OR EXISTS (
                      SELECT 1 FROM service.project_user_assignments pua2
                      WHERE pua2.project_id = p.id AND pua2.user_id = :managerId
                  ))
                """;
    }

    private String buildProjectCountWhere() {
        return """
                WHERE (CAST(:keyword AS text) IS NULL OR (
                    LOWER(p.project_name) LIKE :keyword
                    OR LOWER(COALESCE(p.contract_no, '')) LIKE :keyword))
                  AND (CAST(:statusCode AS text) IS NULL OR p.project_status_code = :statusCode)
                  AND (CAST(:periodFrom AS date) IS NULL OR p.construction_end_date   >= :periodFrom)
                  AND (CAST(:periodTo   AS date) IS NULL OR p.construction_start_date <= :periodTo)
                  AND (CAST(:managerId AS bigint) IS NULL OR EXISTS (
                      SELECT 1 FROM service.project_user_assignments pua2
                      WHERE pua2.project_id = p.id AND pua2.user_id = :managerId
                  ))
                """;
    }

    private MapSqlParameterSource buildProjectParams(
            String keyword, String statusCode, Long managerId,
            LocalDate periodFrom, LocalDate periodTo
    ) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("keyword", keyword != null ? "%" + keyword.toLowerCase() + "%" : null);
        p.addValue("statusCode", statusCode);
        p.addValue("managerId", managerId);
        p.addValue("periodFrom", periodFrom);
        p.addValue("periodTo", periodTo);
        return p;
    }

    private String buildOrderBy(String sortCol, String sortDir) {
        String colSql = (sortCol != null && ALLOWED_SORT_COLS.contains(sortCol))
                ? SORT_COL_SQL.get(sortCol)
                : "p.project_name";
        String dir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        return colSql + " " + dir + " NULLS LAST";
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

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    private Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private LocalDate toLocalDate(Date date) {
        return date != null ? date.toLocalDate() : null;
    }
}
