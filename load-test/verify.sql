-- =============================================================
-- load-test/verify.sql
-- 부하 테스트 후 데이터 정합성 검증
--
-- 실행: locust-local.sh / locust-k8s.sh teardown 직전 자동 호출
--       또는 수동: docker exec safety_db psql -U safety_user -d safety -f - < verify.sql
--
-- 검증 항목:
--   1. 고아 evidence_file_link (item·file 살아있는지)
--   2. 고아 summary (statement 없는 summary)
--   3. agent_logs UNIQUE 위반 (UPSERT 정상 동작 여부)
--   4. 잘못된 usage_statement_items page_no (1 미만)
--   5. revertToDraftIfNeeded 미적용 시그니처 (review_completed인데 최근 PATCH가 있었는지)
--   6. status 분포 — 테스트 후 상태 전이가 다양하게 일어났는지
--   7. files DB count (MinIO 일치 여부는 별도 mc 명령으로)
-- =============================================================
\echo
\echo ========================================
\echo  부하 테스트 데이터 정합성 검증
\echo ========================================
\echo

\echo '--- 1. 고아 evidence_file_link (item 또는 file이 사라진 link) ---'
SELECT
    COUNT(*) FILTER (WHERE i.id IS NULL) AS orphan_by_item,
    COUNT(*) FILTER (WHERE f.id IS NULL) AS orphan_by_file,
    COUNT(*) AS total_links
FROM service.evidence_file_links l
LEFT JOIN service.usage_statement_items i ON i.id = l.usage_statement_item_id
LEFT JOIN service.files f ON f.id = l.file_id
WHERE i.usage_statement_id IN (
    SELECT s.id FROM service.usage_statements s
    JOIN service.projects p ON p.id = s.project_id
    WHERE p.contract_no LIKE 'LOAD-CN-%'
);

\echo
\echo '--- 2. 고아 summary (statement 없는 summary) ---'
SELECT COUNT(*) AS orphan_summaries
FROM service.usage_statement_summaries s
LEFT JOIN service.usage_statements u ON u.id = s.usage_statement_id
WHERE u.id IS NULL;

\echo
\echo '--- 3. agent_logs UNIQUE 위반 (statement + agent_type 중복) ---'
SELECT
    usage_statement_id,
    agent_type_code,
    COUNT(*) AS duplicate_count
FROM service.agent_logs
WHERE usage_statement_item_id IS NULL
AND project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%')
GROUP BY usage_statement_id, agent_type_code
HAVING COUNT(*) > 1
LIMIT 10;

\echo
\echo '--- 4. 잘못된 page_no (1 미만) ---'
SELECT COUNT(*) AS invalid_page_no
FROM service.usage_statement_items i
JOIN service.usage_statements s ON s.id = i.usage_statement_id
JOIN service.projects p ON p.id = s.project_id
WHERE p.contract_no LIKE 'LOAD-CN-%'
AND i.page_no < 1;

\echo
\echo '--- 5. usage_statements 상태 분포 (테스트 후 상태 전이가 일어났는지) ---'
SELECT
    status_code,
    COUNT(*) AS count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 1) AS pct
FROM service.usage_statements
WHERE project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%')
GROUP BY status_code
ORDER BY count DESC;

\echo
\echo '--- 6. files DB 카운트 (MinIO 일치 여부는 mc ls로 별도 확인) ---'
SELECT
    COUNT(*) AS total_files,
    COUNT(*) FILTER (WHERE deleted_at IS NULL) AS active_files,
    COUNT(DISTINCT uploaded_by_user_id) AS distinct_uploaders
FROM service.files
WHERE project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%');

\echo
\echo '--- 7. 누적 데이터 (참고) ---'
SELECT
    (SELECT COUNT(*) FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%') AS projects,
    (SELECT COUNT(*) FROM service.usage_statements WHERE project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%')) AS statements,
    (SELECT COUNT(*) FROM service.usage_statement_items i JOIN service.usage_statements s ON s.id = i.usage_statement_id WHERE s.project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%')) AS items,
    (SELECT COUNT(*) FROM service.evidence_file_links l JOIN service.usage_statement_items i ON i.id = l.usage_statement_item_id JOIN service.usage_statements s ON s.id = i.usage_statement_id WHERE s.project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%')) AS evidence_links,
    (SELECT COUNT(*) FROM service.agent_logs WHERE project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%')) AS agent_logs,
    (SELECT COUNT(*) FROM service.todos WHERE usage_statement_id IN (SELECT id FROM service.usage_statements WHERE project_id IN (SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%'))) AS todos;

\echo
\echo ========================================
\echo  검증 완료
\echo ========================================
