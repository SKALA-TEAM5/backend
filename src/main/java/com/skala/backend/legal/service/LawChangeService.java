package com.skala.backend.legal.service;

import com.skala.backend.auth.security.AuthenticatedUser;
import com.skala.backend.global.error.ApiException;
import com.skala.backend.legal.dto.LawChangeResponses.ChangedLaw;
import com.skala.backend.legal.dto.LawChangeResponses.RecentResponse;
import com.skala.backend.user.domain.RoleCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class LawChangeService {

    private static final String FIND_LATEST_RUN = """
            SELECT l.source_name, l.article_no, l.paragraph_no, l.item_no,
                   l.change_type, MAX(l.changed_at) OVER (PARTITION BY l.run_id) AS last_run_at
            FROM legal_rag.law_log l
            WHERE l.run_id = (
                SELECT run_id FROM legal_rag.law_log ORDER BY changed_at DESC LIMIT 1
            )
            ORDER BY l.source_name, l.article_no
            """;

    private final JdbcTemplate jdbc;

    public LawChangeService(@Qualifier("legalJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public RecentResponse getRecent(AuthenticatedUser currentUser) {
        if (currentUser.roleCode() != RoleCode.SYSTEM_ADMIN
                && currentUser.roleCode() != RoleCode.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");
        }

        List<Row> rows = jdbc.query(FIND_LATEST_RUN, (rs, rowNum) -> new Row(
                rs.getString("source_name"),
                rs.getString("article_no"),
                rs.getString("paragraph_no"),
                rs.getString("item_no"),
                rs.getString("change_type"),
                rs.getTimestamp("last_run_at") != null
                        ? rs.getTimestamp("last_run_at").toInstant() : null
        ));

        if (rows.isEmpty()) {
            return new RecentResponse(false, null, List.of());
        }

        Instant lastRunAt = rows.get(0).lastRunAt();
        List<ChangedLaw> changedLaws = rows.stream()
                .map(r -> new ChangedLaw(r.sourceName(), r.articleNo(), r.paragraphNo(), r.itemNo(), r.changeType()))
                .toList();

        return new RecentResponse(true, lastRunAt, changedLaws);
    }

    private record Row(
            String sourceName,
            String articleNo,
            String paragraphNo,
            String itemNo,
            String changeType,
            Instant lastRunAt
    ) {}
}
