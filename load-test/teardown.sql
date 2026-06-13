-- =============================================================
-- load-test/teardown.sql
-- 부하 테스트 데이터 전체 정리 (seed.sql로 생성된 데이터 역순 삭제)
-- 실행: make locust-teardown
--
-- 주의: 테스트 중 POST /files로 업로드된 MinIO 오브젝트는
--       DB 레코드만 삭제되고 MinIO에는 남음.
--       필요 시 MinIO 콘솔에서 safety-files 버킷의 LOAD-* 경로 수동 삭제.
-- =============================================================
SET search_path TO service, public;

-- ── 식별 기준 ────────────────────────────────────────────────────
-- users:    employee_no LIKE 'LOAD-%'
-- projects: contract_no LIKE 'LOAD-CN-%'

BEGIN;

-- 1. todos (usage_statements CASCADE이지만 명시 삭제)
DELETE FROM todos
WHERE usage_statement_id IN (
    SELECT s.id FROM usage_statements s
    JOIN projects p ON p.id = s.project_id
    WHERE p.contract_no LIKE 'LOAD-CN-%'
);

-- 2. evidence_requirements
DELETE FROM evidence_requirements
WHERE usage_statement_item_id IN (
    SELECT i.id FROM usage_statement_items i
    JOIN usage_statements s ON s.id = i.usage_statement_id
    JOIN projects p ON p.id = s.project_id
    WHERE p.contract_no LIKE 'LOAD-CN-%'
);

-- 3. evidence_file_links
DELETE FROM evidence_file_links
WHERE usage_statement_item_id IN (
    SELECT i.id FROM usage_statement_items i
    JOIN usage_statements s ON s.id = i.usage_statement_id
    JOIN projects p ON p.id = s.project_id
    WHERE p.contract_no LIKE 'LOAD-CN-%'
);

-- 4. agent_logs (statement / item / project FK 참조 → usage_statements·items보다 먼저 삭제)
DELETE FROM agent_logs
WHERE project_id IN (
    SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%'
);

-- 5. agent_usage_records (statement / project FK 참조)
DELETE FROM agent_usage_records
WHERE project_id IN (
    SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%'
);

-- 6. usage_statement_items
DELETE FROM usage_statement_items
WHERE usage_statement_id IN (
    SELECT s.id FROM usage_statements s
    JOIN projects p ON p.id = s.project_id
    WHERE p.contract_no LIKE 'LOAD-CN-%'
);

-- 7. usage_statement_summaries
DELETE FROM usage_statement_summaries
WHERE usage_statement_id IN (
    SELECT s.id FROM usage_statements s
    JOIN projects p ON p.id = s.project_id
    WHERE p.contract_no LIKE 'LOAD-CN-%'
);

-- 8. usage_statements
DELETE FROM usage_statements
WHERE project_id IN (
    SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%'
);

-- 9. files (DB 레코드만 — MinIO 오브젝트는 별도 정리 필요)
DELETE FROM files
WHERE project_id IN (
    SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%'
);

-- 10. project_user_assignments
DELETE FROM project_user_assignments
WHERE project_id IN (
    SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%'
);

-- 11. projects
DELETE FROM projects WHERE contract_no LIKE 'LOAD-CN-%';

-- 12. refresh_tokens
DELETE FROM refresh_tokens
WHERE user_id IN (
    SELECT id FROM users WHERE employee_no LIKE 'LOAD-%'
);

-- 13. users
DELETE FROM users WHERE employee_no LIKE 'LOAD-%';

COMMIT;

-- 확인 쿼리
SELECT
    (SELECT count(*) FROM users       WHERE employee_no LIKE 'LOAD-%')      AS remaining_users,
    (SELECT count(*) FROM projects    WHERE contract_no  LIKE 'LOAD-CN-%')   AS remaining_projects;
