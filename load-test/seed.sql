-- =============================================================
-- load-test/seed.sql
-- 부하 테스트용 계정·프로젝트·사용내역서 사전 생성
-- 비밀번호: P@ssw0rd123! (모든 계정 동일)
-- 실행: make locust-seed
-- =============================================================
SET search_path TO service, public;

-- ── 테스트 계정 ────────────────────────────────────────────────
INSERT INTO users (employee_no, real_name, password_hash, role_code)
SELECT
    'LOAD-ADMIN-' || LPAD(n::text, 3, '0'),
    '부하테스트 관리자' || n,
    '$2y$12$joImXqDiNtrBwXV6wbDo8uoEsKzYfQjD4n6flnTzs8vR6vg9cfV6m',
    'admin'
FROM generate_series(1, 30) n
ON CONFLICT (employee_no) DO NOTHING;

INSERT INTO users (employee_no, real_name, password_hash, role_code)
SELECT
    'LOAD-USER-' || LPAD(n::text, 3, '0'),
    '부하테스트 사용자' || n,
    '$2y$12$joImXqDiNtrBwXV6wbDo8uoEsKzYfQjD4n6flnTzs8vR6vg9cfV6m',
    'user'
FROM generate_series(1, 30) n
ON CONFLICT (employee_no) DO NOTHING;

-- ── 프로젝트 ────────────────────────────────────────────────────
INSERT INTO projects (contract_no, construction_company, project_name, site_location, contract_amount, construction_start_date, construction_end_date, appropriated_amount, project_status_code)
SELECT
    'LOAD-CN-' || LPAD(n::text, 3, '0'),
    '부하테스트건설',
    '부하테스트 프로젝트 ' || n,
    '서울시 강남구',
    100000000,
    '2026-01-01',
    '2026-12-31',
    10000000,
    'active'
FROM generate_series(1, 30) n
WHERE NOT EXISTS (
    SELECT 1 FROM projects p WHERE p.contract_no = 'LOAD-CN-' || LPAD(n::text, 3, '0')
);

-- ── 담당자 배정 (LOAD-USER-N → LOAD-CN-N) ──────────────────────
INSERT INTO project_user_assignments (project_id, user_id, assigned_by_user_id)
SELECT
    p.id,
    u.id,
    a.id
FROM generate_series(1, 30) n
JOIN projects p ON p.contract_no = 'LOAD-CN-' || LPAD(n::text, 3, '0')
JOIN users u ON u.employee_no = 'LOAD-USER-' || LPAD(n::text, 3, '0')
JOIN users a ON a.employee_no = 'LOAD-ADMIN-' || LPAD(n::text, 3, '0')
ON CONFLICT (project_id, user_id) DO NOTHING;

-- ── 사용내역서 (프로젝트당 1개, draft 상태) ─────────────────────
INSERT INTO usage_statements (project_id, report_month, revision_no, document_written_date, cumulative_progress_rate, status_code)
SELECT
    p.id,
    '2026-05-01',
    1,
    '2026-05-15',
    30.00,
    'draft'
FROM generate_series(1, 30) n
JOIN projects p ON p.contract_no = 'LOAD-CN-' || LPAD(n::text, 3, '0')
WHERE NOT EXISTS (
    SELECT 1 FROM usage_statements s WHERE s.project_id = p.id
);

-- ── 세부항목 (사용내역서당 5개, POST /items는 FastAPI를 호출하므로 사전 생성) ──
INSERT INTO usage_statement_items (usage_statement_id, category_code, used_on, item_name, unit, quantity, unit_price, total_amount, page_no)
SELECT
    s.id,
    'CAT_0' || (((n - 1) % 9) + 1),
    '2026-05-15',
    '부하테스트 항목 ' || n,
    '개',
    1,
    10000,
    10000,
    n
FROM usage_statements s
JOIN projects p ON p.id = s.project_id
CROSS JOIN generate_series(1, 5) n
WHERE p.contract_no LIKE 'LOAD-CN-%'
AND NOT EXISTS (
    SELECT 1 FROM usage_statement_items i
    WHERE i.usage_statement_id = s.id
    AND i.item_name = '부하테스트 항목 ' || n
);
