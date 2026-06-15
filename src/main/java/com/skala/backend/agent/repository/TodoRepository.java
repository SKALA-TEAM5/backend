package com.skala.backend.agent.repository;

import com.skala.backend.agent.dto.AgentResponses;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * todos 읽기 모델 테이블 접근 전용 (JdbcTemplate).
 *
 * <p>내용은 {@code agent_logs.details} JSONB에서 파생되며, agent 실행 직후 Spring이 statement 단위로
 * {@link #upsert} + {@link #deleteByStatementExcludingKeys} 조합으로 재생성한다.
 * 식별 키({@code todo_key})는 {@link com.skala.backend.agent.support.TodoKeyGenerator}로 계산한다.
 */
@Repository
public class TodoRepository {

    private static final RowMapper<AgentResponses.TodoResponse> ROW_MAPPER = (rs, n) -> new AgentResponses.TodoResponse(
            rs.getLong("id"),
            rs.getLong("usage_statement_id"),
            (Long) rs.getObject("usage_statement_item_id"),
            rs.getString("usage_statement_item_name"),
            rs.getString("category_code"),
            rs.getString("category_name"),
            rs.getString("agent_type_code"),
            (Long) rs.getObject("file_id"),
            rs.getString("evidence_type_code"),
            rs.getString("evidence_type_name"),
            rs.getString("reason"),
            rs.getBoolean("confirmed"),
            toInstant(rs.getTimestamp("created_at")),
            toInstant(rs.getTimestamp("updated_at"))
    );

    private static java.time.Instant toInstant(java.sql.Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private final JdbcTemplate jdbcTemplate;

    public TodoRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 사용내역서의 TODO 목록. agent_type_code → 생성순으로 안정 정렬. */
    public List<AgentResponses.TodoResponse> findByStatement(Long usageStatementId) {
        return jdbcTemplate.query("""
                SELECT t.id, t.usage_statement_id, t.usage_statement_item_id, t.usage_statement_item_name,
                       t.category_code, t.category_name, t.agent_type_code, t.file_id, t.reason,
                       t.evidence_type_code,
                       COALESCE(et.name, t.evidence_type_code) AS evidence_type_name,
                       t.confirmed, t.created_at, t.updated_at
                FROM service.todos t
                LEFT JOIN service.evidence_types et ON et.code = t.evidence_type_code
                WHERE t.usage_statement_id = ?
                ORDER BY t.agent_type_code, t.id
                """, ROW_MAPPER, usageStatementId);
    }

    /** todo_key 기준 UPSERT. 내용 필드는 갱신하되 confirmed/confirmed_by는 보존한다. */
    public void upsert(String todoKey, Long usageStatementId, Long usageStatementItemId,
            String usageStatementItemName, String categoryCode, String categoryName,
            String agentTypeCode, Long fileId, String reason, String evidenceTypeCode) {
        jdbcTemplate.update("""
                INSERT INTO service.todos
                    (todo_key, usage_statement_id, usage_statement_item_id, usage_statement_item_name,
                     category_code, category_name, agent_type_code, file_id, reason, evidence_type_code)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (todo_key) DO UPDATE SET
                    usage_statement_item_id   = EXCLUDED.usage_statement_item_id,
                    usage_statement_item_name = EXCLUDED.usage_statement_item_name,
                    category_code             = EXCLUDED.category_code,
                    category_name             = EXCLUDED.category_name,
                    agent_type_code           = EXCLUDED.agent_type_code,
                    file_id                   = EXCLUDED.file_id,
                    reason                    = EXCLUDED.reason,
                    evidence_type_code        = EXCLUDED.evidence_type_code,
                    updated_at                = now()
                """, todoKey, usageStatementId, usageStatementItemId, usageStatementItemName,
                categoryCode, categoryName, agentTypeCode, fileId, reason, evidenceTypeCode);
    }

    /** 이번 재생성 결과에 없는 todo_key 행을 제거한다. keys가 비면 statement의 모든 행 삭제. */
    public void deleteByStatementExcludingKeys(Long usageStatementId, Collection<String> keys) {
        if (keys.isEmpty()) {
            jdbcTemplate.update("DELETE FROM service.todos WHERE usage_statement_id = ?", usageStatementId);
            return;
        }
        String placeholders = String.join(",", keys.stream().map(k -> "?").toList());
        Object[] params = new Object[keys.size() + 1];
        params[0] = usageStatementId;
        int i = 1;
        for (String k : keys) params[i++] = k;
        jdbcTemplate.update(
                "DELETE FROM service.todos WHERE usage_statement_id = ? AND todo_key NOT IN (" + placeholders + ")",
                params);
    }

    /** 확인(체크) 토글. 영향 행 수를 반환한다(0이면 대상 없음). */
    public int updateConfirmed(Long todoId, boolean confirmed, Long confirmedBy) {
        return jdbcTemplate.update("""
                UPDATE service.todos
                SET confirmed = ?, confirmed_by = ?, updated_at = now()
                WHERE id = ?
                """, confirmed, confirmed ? confirmedBy : null, todoId);
    }

    /** todoId가 속한 프로젝트 ID. 없으면 null. 접근 제어 검증용. */
    public Long findProjectIdByTodoId(Long todoId) {
        List<Long> result = jdbcTemplate.query("""
                SELECT us.project_id
                FROM service.todos t
                JOIN service.usage_statements us ON us.id = t.usage_statement_id
                WHERE t.id = ?
                """, (rs, n) -> rs.getLong("project_id"), todoId);
        return result.isEmpty() ? null : result.get(0);
    }
}
