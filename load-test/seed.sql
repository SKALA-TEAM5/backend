-- =============================================================
-- load-test/seed.sql
-- 부하 테스트용 대규모 데이터 시딩 (의미 있는 검색·페이징·집계 측정용)
-- 비밀번호: P@ssw0rd123! (모든 계정 동일)
-- 실행: bash load-test/locust-local.sh seed
-- 정리: bash load-test/locust-local.sh teardown
--
-- 규모:
--   계정       : admin 500 + user 500 = 1,000
--   프로젝트   : 2,000개  (LOAD-CN-0001 ~ LOAD-CN-2000)
--   PUA        : 약 8,000행 (각 프로젝트당 admin 2 + user 2)
--                각 admin/user는 평균 8개 프로젝트에 배정 (m:n)
--   사용내역서 : 12,000개 (프로젝트당 6개월치, 2026-01 ~ 2026-06)
--                상태 분포 — draft 60% / upload 20% / supp 10% / review 10%
--   세부항목   : 180,000개 (statement당 15개)
--   agent_logs : 36,000개 (statement당 classi/safety-doc/legal success)
--
-- 식별 규칙: 모든 LOAD-* prefix, 운영 데이터와 격리
-- =============================================================
SET search_path TO service, public;

-- ── 1. 계정: admin 500 + user 500 ───────────────────────────────
INSERT INTO users (employee_no, real_name, password_hash, role_code)
SELECT
    'LOAD-ADMIN-' || LPAD(n::text, 3, '0'),
    '부하테스트 관리자' || n,
    '$2y$12$joImXqDiNtrBwXV6wbDo8uoEsKzYfQjD4n6flnTzs8vR6vg9cfV6m',
    'admin'
FROM generate_series(1, 500) n
ON CONFLICT (employee_no) DO NOTHING;

INSERT INTO users (employee_no, real_name, password_hash, role_code)
SELECT
    'LOAD-USER-' || LPAD(n::text, 3, '0'),
    '부하테스트 사용자' || n,
    '$2y$12$joImXqDiNtrBwXV6wbDo8uoEsKzYfQjD4n6flnTzs8vR6vg9cfV6m',
    'user'
FROM generate_series(1, 500) n
ON CONFLICT (employee_no) DO NOTHING;

-- ── 2. 프로젝트 2,000개 (LOAD-CN-0001 ~ LOAD-CN-2000) ───────────
-- 상태·금액·지역 분포 추가 (검색·필터 의미 확보)
INSERT INTO projects (
    contract_no, construction_company, project_name,
    site_location, contract_amount,
    construction_start_date, construction_end_date,
    appropriated_amount, project_status_code
)
SELECT
    'LOAD-CN-' || LPAD(n::text, 4, '0'),
    CASE (n % 4) WHEN 0 THEN '부하테스트건설' WHEN 1 THEN '안전관리건설' WHEN 2 THEN '스마트건설' ELSE '대한건설' END,
    '부하테스트 프로젝트 ' || n,
    CASE (n % 5) WHEN 0 THEN '서울시 강남구' WHEN 1 THEN '경기도 성남시' WHEN 2 THEN '부산시 해운대구' WHEN 3 THEN '대구시 수성구' ELSE '인천시 부평구' END,
    (100000000 + (n % 10) * 50000000)::numeric,
    DATE '2025-07-01' + ((n % 12) * INTERVAL '15 days'),
    DATE '2026-12-31' + ((n % 6) * INTERVAL '30 days'),
    (10000000 + (n % 20) * 500000)::numeric,
    CASE (n % 10)
        WHEN 0 THEN 'completed'
        WHEN 1 THEN 'suspended'
        ELSE 'active'
    END
FROM generate_series(1, 2000) n
WHERE NOT EXISTS (
    SELECT 1 FROM projects p WHERE p.contract_no = 'LOAD-CN-' || LPAD(n::text, 4, '0')
);

-- ── 3. project_user_assignments (m:n 매핑) ─────────────────────
-- 각 프로젝트 k에 다음을 배정:
--   admin1 = LOAD-ADMIN-(((k-1) % 500) + 1)
--   admin2 = LOAD-ADMIN-((k+249) % 500 + 1)  -- shift 250
--   user1  = LOAD-USER-(((k-1) % 500) + 1)
--   user2  = LOAD-USER-((k+249) % 500 + 1)
-- 결과: admin/user 각각 평균 8개 프로젝트 (2000×2/500), 모든 admin·user가 PUA에 직접 등록됨
WITH project_pool AS (
    SELECT id, SUBSTR(contract_no, 9)::int AS k
    FROM projects
    WHERE contract_no LIKE 'LOAD-CN-%'
),
assignments AS (
    -- 프로젝트당 4개 assignment (admin1, admin2, user1, user2)
    SELECT p.id AS project_id, 'LOAD-ADMIN-' || LPAD((((p.k - 1) % 500) + 1)::text, 3, '0') AS emp_no FROM project_pool p
    UNION ALL
    SELECT p.id, 'LOAD-ADMIN-' || LPAD((((p.k + 249) % 500) + 1)::text, 3, '0')           FROM project_pool p
    UNION ALL
    SELECT p.id, 'LOAD-USER-'  || LPAD((((p.k - 1) % 500) + 1)::text, 3, '0')             FROM project_pool p
    UNION ALL
    SELECT p.id, 'LOAD-USER-'  || LPAD((((p.k + 249) % 500) + 1)::text, 3, '0')           FROM project_pool p
)
INSERT INTO project_user_assignments (project_id, user_id, assigned_by_user_id)
SELECT a.project_id, u.id, u.id
FROM assignments a
JOIN users u ON u.employee_no = a.emp_no
ON CONFLICT (project_id, user_id) DO NOTHING;

-- ── 4. usage_statements (프로젝트당 6개월치, 상태 분포) ────────
-- 2026-01 ~ 2026-06, 최신 월(6월)은 항상 draft
-- 그 외 월은 분포: draft 60% / upload 20% / supp 10% / review 10%
INSERT INTO usage_statements (
    project_id, report_month, revision_no,
    document_written_date, cumulative_progress_rate, status_code
)
SELECT
    p.id,
    (DATE '2026-01-01' + ((m - 1) * INTERVAL '1 month'))::date,
    1,
    (DATE '2026-01-15' + ((m - 1) * INTERVAL '1 month'))::date,
    LEAST(m * 16.66, 100.0)::numeric(5,2),
    CASE
        WHEN m = 6 THEN 'draft'
        ELSE (
            CASE (((p.id * 7 + m * 13) % 10))
                WHEN 0 THEN 'review_completed'
                WHEN 1 THEN 'supplement_required'
                WHEN 2 THEN 'upload_completed'
                WHEN 3 THEN 'upload_completed'
                ELSE 'draft'
            END
        )
    END
FROM projects p
CROSS JOIN generate_series(1, 6) m
WHERE p.contract_no LIKE 'LOAD-CN-%'
AND NOT EXISTS (
    SELECT 1 FROM usage_statements s
    WHERE s.project_id = p.id
    AND s.report_month = (DATE '2026-01-01' + ((m - 1) * INTERVAL '1 month'))::date
    AND s.revision_no = 1
);

-- ── 5. usage_statement_items (statement당 15개) ────────────────
INSERT INTO usage_statement_items (
    usage_statement_id, category_code, used_on,
    item_name, unit, quantity, unit_price, total_amount, page_no
)
SELECT
    s.id,
    'CAT_0' || (((n - 1) % 9) + 1),
    (s.report_month + ((n % 28)::int) * INTERVAL '1 day')::date,
    '부하테스트 항목 ' || n,
    CASE (n % 3) WHEN 0 THEN '개' WHEN 1 THEN '식' ELSE '시간' END,
    (1 + (n % 5))::numeric,
    (10000 + (n % 10) * 1000)::numeric,
    ((1 + (n % 5)) * (10000 + (n % 10) * 1000))::numeric,
    n
FROM usage_statements s
JOIN projects p ON p.id = s.project_id
CROSS JOIN generate_series(1, 15) n
WHERE p.contract_no LIKE 'LOAD-CN-%'
AND NOT EXISTS (
    SELECT 1 FROM usage_statement_items i
    WHERE i.usage_statement_id = s.id
    AND i.item_name = '부하테스트 항목 ' || n
);

-- ── 6. agent_logs 시딩 (classi / safety-doc / legal success) ──
-- 목적:
--   PATCH /usage-statements/{id}/submit, /request-supplement, /complete-review가
--   legal·safety-doc 로그를 강제(requireLegalCompleted)하므로
--   FastAPI 미호출 부하 테스트에서 상태 전이가 정상 측정되도록 가상 success 로그 시드.
-- 제약: UNIQUE(usage_statement_id, agent_type_code) WHERE usage_statement_item_id IS NULL
INSERT INTO agent_logs (
    project_id, usage_statement_id, agent_type_code,
    status_code, result_code, reason, details
)
SELECT
    s.project_id,
    s.id,
    t.agent_type_code,
    'success',
    'success',
    '[load-test seed] ' || t.agent_type_code || ' 가상 성공',
    '{}'::jsonb
FROM usage_statements s
JOIN projects p ON p.id = s.project_id
CROSS JOIN (VALUES ('classi'), ('safety-doc'), ('legal')) AS t(agent_type_code)
WHERE p.contract_no LIKE 'LOAD-CN-%'
AND NOT EXISTS (
    SELECT 1 FROM agent_logs a
    WHERE a.usage_statement_id = s.id
    AND a.agent_type_code = t.agent_type_code
    AND a.usage_statement_item_id IS NULL
);

-- ── 확인 출력 ─────────────────────────────────────────────────
SELECT
    (SELECT count(*) FROM users WHERE employee_no LIKE 'LOAD-ADMIN-%') AS admins,
    (SELECT count(*) FROM users WHERE employee_no LIKE 'LOAD-USER-%')  AS users,
    (SELECT count(*) FROM projects WHERE contract_no LIKE 'LOAD-CN-%') AS projects,
    (SELECT count(*) FROM project_user_assignments
        WHERE project_id IN (SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%')) AS assignments,
    (SELECT count(*) FROM usage_statements
        WHERE project_id IN (SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%')) AS statements,
    (SELECT count(*) FROM usage_statement_items i
        JOIN usage_statements s ON s.id = i.usage_statement_id
        WHERE s.project_id IN (SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%')) AS items,
    (SELECT count(*) FROM agent_logs
        WHERE project_id IN (SELECT id FROM projects WHERE contract_no LIKE 'LOAD-CN-%')) AS agent_logs;
