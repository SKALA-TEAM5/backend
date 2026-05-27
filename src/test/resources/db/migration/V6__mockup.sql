-- =============================================================
-- V6__mockup.sql
-- 개발 로그인 계정 + 시나리오 목업 데이터 시드
-- 프로젝트: 동탄(조치요청) / 평택(보고서작성중) / 광명(업로드대기)
-- =============================================================
SET LOCAL search_path TO service, public;

-- ─────────────────────────────────────────────────────────────
-- 1. 사용자
--    SYS-001 기본 비밀번호: P@ssw0rd123!
--    ADMIN-* 기본 비밀번호: Admin1234!
--    USER-* 기본 비밀번호: User1234!
-- ─────────────────────────────────────────────────────────────
INSERT INTO users (employee_no, real_name, password_hash, role_code) VALUES
    -- 시스템 관리자
    ('SYS-001', '시스템관리자', '$2y$12$joImXqDiNtrBwXV6wbDo8uoEsKzYfQjD4n6flnTzs8vR6vg9cfV6m', 'system_admin'),
    -- 개발 로그인 계정
    ('ADMIN-001', '김서연', '$2y$12$fu40qSD1PjbzuaBTuhs4LONH3xgoXCNimu8Q/9b.OknMpRBruyQDu', 'admin'),
    ('ADMIN-002', '박민준', '$2y$12$fu40qSD1PjbzuaBTuhs4LONH3xgoXCNimu8Q/9b.OknMpRBruyQDu', 'admin'),
    ('USER-001', '이현우', '$2y$12$GvLF2ME1SyQh/UCcMBzb.Ogv2dTpsx8BqsWxQJbIekauTDauICKj2', 'user'),
    ('USER-002', '최지훈', '$2y$12$GvLF2ME1SyQh/UCcMBzb.Ogv2dTpsx8BqsWxQJbIekauTDauICKj2', 'user'),
    ('USER-003', '정유진', '$2y$12$GvLF2ME1SyQh/UCcMBzb.Ogv2dTpsx8BqsWxQJbIekauTDauICKj2', 'user'),
    ('USER-004', '한도윤', '$2y$12$GvLF2ME1SyQh/UCcMBzb.Ogv2dTpsx8BqsWxQJbIekauTDauICKj2', 'user'),
    -- SHE 담당자
    ('SHE-001', '홍길동',  '$2b$12$placeholder_hash_hong',   'admin'),
    ('SHE-002', '최안전',  '$2b$12$placeholder_hash_choi',   'admin'),
    ('SHE-003', '이검토',  '$2b$12$placeholder_hash_lee_r',  'admin'),
    -- 프로젝트 담당자 (동탄)
    ('USR-101', '김현장',  '$2b$12$placeholder_hash_kim',    'user'),
    -- 프로젝트 담당자 (평택)
    ('USR-201', '박공무',  '$2b$12$placeholder_hash_park',   'user'),
    ('USR-202', '오정산',  '$2b$12$placeholder_hash_oh',     'user'),
    -- 프로젝트 담당자 (광명)
    ('USR-301', '이프로',  '$2b$12$placeholder_hash_lee_p',  'user'),
    ('USR-302', '정현장',  '$2b$12$placeholder_hash_jung',   'user')
ON CONFLICT (employee_no) DO UPDATE
SET
    real_name = EXCLUDED.real_name,
    password_hash = EXCLUDED.password_hash,
    role_code = EXCLUDED.role_code;

-- ─────────────────────────────────────────────────────────────
-- 2. 프로젝트
-- ─────────────────────────────────────────────────────────────
INSERT INTO projects (
    contract_no, construction_company, project_name,
    site_location, representative_name, contract_amount,
    construction_start_date, construction_end_date,
    client_name, appropriated_amount, project_status_code
) VALUES
    -- 동탄 물류센터 (조치 요청)
    (
        '2024-0042', '스칼라건설', '동탄 물류센터 증축공사',
        '경기도 화성시 동탄물류단지', '정대표', 12000000000,
        '2024-10-23', '2025-06-21',
        '동탄물류센터', 12000000000, 'active'
    ),
    -- 평택 제조시설 (보고서 작성 중)
    (
        '2024-0108', '평택산업개발', '평택 제조시설 증설',
        '경기도 평택시 고덕산업단지', '강대표', 8500000000,
        '2023-06-01', '2024-12-31',
        '평택제조시설', 8500000000, 'active'
    ),
    -- 광명 데이터센터 (업로드 대기)
    (
        '2025-0016', '광명디씨건설', '광명 데이터센터 신축',
        '경기도 광명시 첨단산업지구', '문대표', 15700000000,
        '2025-02-01', '2026-08-31',
        '광명데이터센터', 15700000000, 'active'
    )
ON CONFLICT DO NOTHING;

INSERT INTO project_user_assignments (project_id, user_id, assigned_by_user_id)
VALUES
    ((SELECT id FROM projects WHERE contract_no = '2024-0042'), (SELECT id FROM users WHERE employee_no = 'USR-101'), (SELECT id FROM users WHERE employee_no = 'ADMIN-001')),
    ((SELECT id FROM projects WHERE contract_no = '2024-0108'), (SELECT id FROM users WHERE employee_no = 'USR-201'), (SELECT id FROM users WHERE employee_no = 'ADMIN-001')),
    ((SELECT id FROM projects WHERE contract_no = '2025-0016'), (SELECT id FROM users WHERE employee_no = 'USR-301'), (SELECT id FROM users WHERE employee_no = 'ADMIN-001'))
ON CONFLICT (project_id, user_id) DO NOTHING;

-- 편의 변수: 프로젝트 ID 를 서브쿼리로 참조
-- ─────────────────────────────────────────────────────────────
-- 3. 파일 (동탄)
-- ─────────────────────────────────────────────────────────────

-- 동탄 사용내역서 파일 (xlsx)
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'usage_statement',
        '동탄_산안비_사용내역서_2026-04.xlsx',
        'projects/dt-logistics/statements/동탄_산안비_사용내역서_2026-04.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        204800,
        '2026-04-23 09:10:00+09'
    );

-- 동탄 증빙 파일들 (사용내역서 type)
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'usage_statement',
        '사용내역서_안전관리자_1분기.pdf',
        'projects/dt-logistics/evidence/사용내역서_안전관리자_1분기.pdf',
        'application/pdf', 512000, '2026-04-23 09:15:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'usage_statement',
        '사용내역서_보호구_1분기.xlsx',
        'projects/dt-logistics/evidence/사용내역서_보호구_1분기.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        153600, '2026-04-23 09:15:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'usage_statement',
        '사용내역서_안전시설_2분기.pdf',
        'projects/dt-logistics/evidence/사용내역서_안전시설_2분기.pdf',
        'application/pdf', 409600, '2026-04-23 09:16:00+09'
    );

-- 동탄 영수증 파일들
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'transaction_statement',
        '안전관리자_선임수수료_1월.pdf',
        'projects/dt-logistics/evidence/안전관리자_선임수수료_1월.pdf',
        'application/pdf', 307200, '2026-04-23 09:20:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'transaction_statement',
        '안전모_구입_영수증.jpg',
        'projects/dt-logistics/evidence/안전모_구입_영수증.jpg',
        'image/jpeg', 204800, '2026-04-23 09:20:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'transaction_statement',
        '안전화_구입_영수증.jpg',
        'projects/dt-logistics/evidence/안전화_구입_영수증.jpg',
        'image/jpeg', 184320, '2026-04-23 09:21:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'transaction_statement',
        '보호구_세트_영수증.pdf',
        'projects/dt-logistics/evidence/보호구_세트_영수증.pdf',
        'application/pdf', 245760, '2026-04-23 09:21:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'transaction_statement',
        '안전난간_영수증.jpg',
        'projects/dt-logistics/evidence/안전난간_영수증.jpg',
        'image/jpeg', 163840, '2026-04-23 09:22:00+09'
    );

-- 동탄 현장사진
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, captured_at, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'wearing_photo',
        '안전관리자_현장순찰_사진.jpg',
        'projects/dt-logistics/evidence/안전관리자_현장순찰_사진.jpg',
        'image/jpeg', 3145728,
        '2026-04-10 10:30:00+09', '2026-04-23 09:25:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'wearing_photo',
        '안전모_착용_현장.jpg',
        'projects/dt-logistics/evidence/안전모_착용_현장.jpg',
        'image/jpeg', 2621440,
        '2026-04-11 11:00:00+09', '2026-04-23 09:25:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'wearing_photo',
        '안전화_지급_사진.jpg',
        'projects/dt-logistics/evidence/안전화_지급_사진.jpg',
        'image/jpeg', 2097152,
        '2026-04-12 09:00:00+09', '2026-04-23 09:26:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'wearing_photo',
        '보호구_착용확인.jpg',
        'projects/dt-logistics/evidence/보호구_착용확인.jpg',
        'image/jpeg', 2359296,
        '2026-04-13 14:00:00+09', '2026-04-23 09:26:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'site_photo',
        '안전시설_전체사진.jpg',
        'projects/dt-logistics/evidence/안전시설_전체사진.jpg',
        'image/jpeg', 4194304,
        '2026-04-14 15:00:00+09', '2026-04-23 09:27:00+09'
    );

-- 동탄 세금내역서 + 제3자사실관계확인서
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'tax_invoice',
        '세금내역서_안전관리자_1월.pdf',
        'projects/dt-logistics/evidence/세금내역서_안전관리자_1월.pdf',
        'application/pdf', 409600, '2026-04-23 09:30:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'tax_invoice_confirm',
        '제3자사실관계확인서_안전관리자_1월.pdf',
        'projects/dt-logistics/evidence/제3자사실관계확인서_안전관리자_1월.pdf',
        'application/pdf', 307200, '2026-04-23 09:30:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'tax_invoice',
        '세금내역서_보호구_4월.pdf',
        'projects/dt-logistics/evidence/세금내역서_보호구_4월.pdf',
        'application/pdf', 358400, '2026-04-23 09:31:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'tax_invoice_confirm',
        '제3자사실관계확인서_보호구_4월.pdf',
        'projects/dt-logistics/evidence/제3자사실관계확인서_보호구_4월.pdf',
        'application/pdf', 286720, '2026-04-23 09:31:30+09'
    );

-- 동탄 기타 서류
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'appointment_report',
        '안전관리자_선임계.pdf',
        'projects/dt-logistics/evidence/안전관리자_선임계.pdf',
        'application/pdf', 204800, '2026-04-23 09:35:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'supply_ledger',
        '보호구_지급대장.xlsx',
        'projects/dt-logistics/evidence/보호구_지급대장.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        122880, '2026-04-23 09:35:30+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0042'),
        (SELECT id FROM users WHERE employee_no = 'USR-101'),
        'site_photo',
        '안전시설_설치확인서.pdf',
        'projects/dt-logistics/evidence/안전시설_설치확인서.pdf',
        'application/pdf', 307200, '2026-04-23 09:36:00+09'
    );

-- ─────────────────────────────────────────────────────────────
-- 4. 사용내역서 (동탄 4차 2026-04)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statements (
    project_id, source_file_id, report_month, revision_no,
    document_written_date, cumulative_progress_rate
) VALUES (
    (SELECT id FROM projects WHERE contract_no = '2024-0042'),
    (SELECT id FROM files WHERE storage_key = 'projects/dt-logistics/statements/동탄_산안비_사용내역서_2026-04.xlsx'),
    '2026-04-01',
    4,
    '2026-04-22',
    78.00
);

-- ─────────────────────────────────────────────────────────────
-- 5. 사용내역서 요약 (동탄)
--    금회 합계 = 7,840,000 / 누계 = 48,614,045
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statement_summaries (
    usage_statement_id, category_code, previous_amount, current_amount, cumulative_amount
)
SELECT
    us.id,
    v.category_code,
    v.previous_amount,
    v.current_amount,
    v.cumulative_amount
FROM usage_statements us
CROSS JOIN (VALUES
    -- CAT_01 안전·보건관리자 임금 등 (정상)
    ('CAT_01', 15000000::NUMERIC, 2500000::NUMERIC, 17500000::NUMERIC),
    -- CAT_02 안전시설비 등 (조건부 인정, 850,000 문제)
    ('CAT_02',  8200000::NUMERIC, 1940000::NUMERIC, 10140000::NUMERIC),
    -- CAT_03 보호구 등 (부적정, 금액 불일치 220,000)
    ('CAT_03',  9500000::NUMERIC, 1200000::NUMERIC, 10700000::NUMERIC),
    -- CAT_04 안전보건진단비 등
    ('CAT_04',  1500000::NUMERIC,       0::NUMERIC,  1500000::NUMERIC),
    -- CAT_05 근로자 건강장해예방비 등
    ('CAT_05',  2000000::NUMERIC,  500000::NUMERIC,  2500000::NUMERIC),
    -- CAT_06 건설재해예방전문지도기관 기술지도비 (안전보건교육비)
    ('CAT_06',  1200000::NUMERIC,  700000::NUMERIC,  1900000::NUMERIC),
    -- CAT_07 본사 전담조직 근로자 임금 등 (부적정, 한도 초과)
    ('CAT_07',  3474045::NUMERIC, 1000000::NUMERIC,  4474045::NUMERIC),
    -- CAT_08 위험성평가 등에 따른 소요비용
    ('CAT_08',        0::NUMERIC,        0::NUMERIC,        0::NUMERIC)
) AS v(category_code, previous_amount, current_amount, cumulative_amount)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01';

-- ─────────────────────────────────────────────────────────────
-- 6. 사용내역서 상세 항목 (동탄 핵심 3개 문제 항목)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statement_items (
    usage_statement_id, category_code, used_on, item_name,
    unit, quantity, unit_price, total_amount, remark, page_no
)
SELECT
    us.id,
    v.category_code,
    v.used_on::DATE,
    v.item_name,
    v.unit,
    v.quantity,
    v.unit_price,
    v.total_amount,
    v.remark,
    v.page_no
FROM usage_statements us
CROSS JOIN (VALUES
    -- CAT_01 안전관리자 임금 (정상)
    ('CAT_01', '2026-04-30', '안전관리자 임금 (4월)',         '월', 1, 2500000.00, 2500000, '정규 선임 안전관리자', 3),
    -- CAT_02 안전시설비 - 안전난간 부품 (조건부 인정)
    ('CAT_02', '2026-04-15', '안전난간 설치 부품 구매',       '식', 1,  850000.00,  850000, '안전난간 부품 일체', 7),
    ('CAT_02', '2026-04-20', '안전표지판 설치',               '개', 6,  180000.00, 1080000, '현장 진입로 표지판', 8),
    -- CAT_03 보호구 (부적정: 내역서 1,200,000 vs 영수증 합계 980,000)
    ('CAT_03', '2026-04-05', '안전모 구입',                   '개', 20,  30000.00,  600000, '신규 입사자용 안전모', 11),
    ('CAT_03', '2026-04-08', '안전화 구입',                   '켤레', 10, 45000.00, 450000, '안전화 10켤레', 12),
    ('CAT_03', '2026-04-10', '안전벨트 구입',                 '개',  5,  30000.00,  150000, '고소작업용 안전벨트', 13),
    -- CAT_07 본사 전담조직 임금 (한도 초과)
    ('CAT_07', '2026-04-30', '본사 전담조직 안전담당자 임금', '월', 1, 1000000.00, 1000000, '한도 초과 의심', 18)
) AS v(category_code, used_on, item_name, unit, quantity, unit_price, total_amount, remark, page_no)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01';

-- ─────────────────────────────────────────────────────────────
-- 7. 증빙 파일 링크 (동탄 항목 ↔ 파일)
-- ─────────────────────────────────────────────────────────────

-- CAT_01 안전관리자 임금 ↔ 관련 파일
INSERT INTO evidence_file_links (usage_statement_item_id, file_id, evidence_type_code)
SELECT
    item.id,
    f.id,
    f.uploaded_evidence_type_code
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
JOIN files f ON f.project_id = us.project_id
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.item_name = '안전관리자 임금 (4월)'
  AND f.original_filename IN (
      '안전관리자_선임수수료_1월.pdf',
      '세금내역서_안전관리자_1월.pdf',
      '제3자사실관계확인서_안전관리자_1월.pdf',
      '안전관리자_선임계.pdf',
      '안전관리자_현장순찰_사진.jpg'
  );

-- CAT_03 안전모 구입 ↔ 관련 파일
INSERT INTO evidence_file_links (usage_statement_item_id, file_id, evidence_type_code)
SELECT
    item.id,
    f.id,
    f.uploaded_evidence_type_code
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
JOIN files f ON f.project_id = us.project_id
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.item_name = '안전모 구입'
  AND f.original_filename IN ('안전모_구입_영수증.jpg', '안전모_착용_현장.jpg');

-- CAT_03 안전화 구입 ↔ 관련 파일
INSERT INTO evidence_file_links (usage_statement_item_id, file_id, evidence_type_code)
SELECT
    item.id,
    f.id,
    f.uploaded_evidence_type_code
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
JOIN files f ON f.project_id = us.project_id
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.item_name = '안전화 구입'
  AND f.original_filename IN ('안전화_구입_영수증.jpg', '안전화_지급_사진.jpg');

-- CAT_03 안전벨트 구입 ↔ 관련 파일
INSERT INTO evidence_file_links (usage_statement_item_id, file_id, evidence_type_code)
SELECT
    item.id,
    f.id,
    f.uploaded_evidence_type_code
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
JOIN files f ON f.project_id = us.project_id
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.item_name = '안전벨트 구입'
  AND f.original_filename IN ('보호구_세트_영수증.pdf', '보호구_착용확인.jpg', '보호구_지급대장.xlsx');

-- ─────────────────────────────────────────────────────────────
-- 8. 증빙 요건 (동탄 문제 항목)
--    CAT_03: wearing_photo 미충족, supply_ledger 미충족
-- ─────────────────────────────────────────────────────────────
INSERT INTO evidence_requirements (usage_statement_item_id, evidence_type_code, is_satisfied)
SELECT item.id, req.evidence_type_code, req.is_satisfied
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
CROSS JOIN (VALUES
    ('tax_invoice',      true),
    ('wearing_photo',    false),  -- 착용 상태 불명확
    ('supply_ledger',    false),  -- 지급대장 누락
    ('transaction_statement', true)
) AS req(evidence_type_code, is_satisfied)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.category_code = 'CAT_03';

INSERT INTO evidence_requirements (usage_statement_item_id, evidence_type_code, is_satisfied)
SELECT item.id, req.evidence_type_code, req.is_satisfied
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
CROSS JOIN (VALUES
    ('transaction_statement', true),
    ('site_photo',       false)  -- 설치 완료 사진 부족
) AS req(evidence_type_code, is_satisfied)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.item_name = '안전난간 설치 부품 구매';

-- ─────────────────────────────────────────────────────────────
-- 9. 검증 로그 (동탄)
-- ─────────────────────────────────────────────────────────────
INSERT INTO agent_logs (
    project_id, usage_statement_id,
    agent_type_code, status_code, details, model_name, created_at
)
SELECT
    us.project_id,
    us.id,
    vd.agent_type_code,
    'success',
    vd.details::JSONB,
    'claude-sonnet-4-20250514',
    '2026-04-23 11:00:00+09'
FROM usage_statements us
CROSS JOIN (VALUES
    ('vision',  '{"result":"issues_found","issues":[{"item":"안전모 구입","issue":"턱끈 미체결, 지급대장 불일치"},{"item":"안전화 구입","issue":"착용 사진 부족"},{"item":"안전벨트 구입","issue":"체결 부위 미확인"},{"item":"안전난간 설치 부품 구매","issue":"설치 위치 사진 부족"}]}'),
    ('classi',  '{"result":"issues_found","issues":[{"item":"안전모 구입","issue":"금액 불일치","statement_amount":1200000,"receipt_total":980000,"difference":220000}]}'),
    ('legal',   '{"result":"issues_found","issues":[{"item":"본사 전담조직 안전담당자 임금","issue":"한도 초과","limit_rate":0.06,"claimed_amount":4800000}]}')
) AS vd(agent_type_code, details)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01';

-- ─────────────────────────────────────────────────────────────
-- 10. 조치 요청 (동탄)
-- ─────────────────────────────────────────────────────────────
INSERT INTO action_requests (
    project_id, usage_statement_id, usage_statement_item_id,
    requested_by_user_id, assignee_user_id,
    title, reason, status_code, due_date, created_at
)
SELECT
    us.project_id,
    us.id,
    item.id,
    (SELECT id FROM users WHERE employee_no = 'SHE-001'),   -- 홍길동 (SHE)
    (SELECT id FROM users WHERE employee_no = 'USR-101'),   -- 김현장
    ar.title,
    ar.reason,
    'open',
    '2026-04-26',
    '2026-04-23 11:02:00+09'
FROM usage_statements us
JOIN usage_statement_items item ON item.usage_statement_id = us.id
CROSS JOIN (VALUES
    ('CAT_03', '안전모 구입',
     '개인보호구 증빙 보완 요청 - 안전모',
     '안전모 착용 사진 내 턱끈 미체결 확인 및 지급대장 인원과 사진 속 인원 불일치. 지급대장 보완본 및 명확한 착용 현장사진 제출 필요'),
    ('CAT_03', '안전화 구입',
     '개인보호구 증빙 보완 요청 - 안전화',
     '안전화 실제 작업 중 착용 사진 부재. 지급 장면 외 작업 현장 착용 사진 제출 필요'),
    ('CAT_03', '안전벨트 구입',
     '개인보호구 증빙 보완 요청 - 안전벨트',
     '안전벨트 체결 부위가 가려져 착용 적정성 판단 불가. 체결 상태가 명확히 보이는 사진 제출 및 누락 영수증 220,000원 추가 제출 필요'),
    ('CAT_02', '안전난간 설치 부품 구매',
     '안전시설비 현장 설치 확인 사진 요청',
     '안전난간 부품 구매 영수증 존재하나 설치 위치 및 완성 상태 사진 없음. 설치 전후 비교 및 전체 현장 사진 제출 필요'),
    ('CAT_07', '본사 전담조직 안전담당자 임금',
     '본사 전담조직 계상금액 한도 초과 정정 요청',
     '본사 전담조직 사용비 계상액이 허용 한도를 초과. 초과분 정정 및 산정 근거 서류 제출, 정산 가능 금액 재계산 필요')
) AS ar(category_code, item_name, title, reason)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0042')
  AND us.report_month = '2026-04-01'
  AND item.category_code = ar.category_code
  AND item.item_name     = ar.item_name;

-- ═════════════════════════════════════════════════════════════
-- 평택 제조시설 (보고서 작성 중 - 정상 시나리오)
-- ═════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- 11. 파일 (평택)
-- ─────────────────────────────────────────────────────────────
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    -- 사용내역서 파일 (4월)
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'usage_statement',
        '평택_사용내역서_2026-04.xlsx',
        'projects/pt-manufacturing/statements/평택_사용내역서_2026-04.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        174080, '2026-04-21 09:00:00+09'
    ),
    -- 사용내역서 파일 (3월)
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'usage_statement',
        '평택_사용내역서_2026-03.xlsx',
        'projects/pt-manufacturing/statements/평택_사용내역서_2026-03.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        163840, '2026-03-21 09:00:00+09'
    ),
    -- 증빙 영수증
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'transaction_statement',
        '보건관리_용역비_청구서.pdf',
        'projects/pt-manufacturing/evidence/보건관리_용역비_청구서.pdf',
        'application/pdf', 307200, '2026-04-21 09:10:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'transaction_statement',
        '보건관리자_4월_영수증.jpg',
        'projects/pt-manufacturing/evidence/보건관리자_4월_영수증.jpg',
        'image/jpeg', 204800, '2026-04-21 09:11:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'transfer_confirm',
        '교육비_납입증명서.pdf',
        'projects/pt-manufacturing/evidence/교육비_납입증명서.pdf',
        'application/pdf', 245760, '2026-04-21 09:12:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'transaction_statement',
        '위험성평가_컨설팅비.pdf',
        'projects/pt-manufacturing/evidence/위험성평가_컨설팅비.pdf',
        'application/pdf', 286720, '2026-04-21 09:13:00+09'
    ),
    -- 현장사진
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'work_photo',
        '보건관리자_현장방문.jpg',
        'projects/pt-manufacturing/evidence/보건관리자_현장방문.jpg',
        'image/jpeg', 2621440, '2026-04-21 09:20:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'inspection_log',
        '보건시설_점검_사진.jpg',
        'projects/pt-manufacturing/evidence/보건시설_점검_사진.jpg',
        'image/jpeg', 2097152, '2026-04-21 09:21:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'work_photo',
        '보건관리_활동사진.jpg',
        'projects/pt-manufacturing/evidence/보건관리_활동사진.jpg',
        'image/jpeg', 1835008, '2026-04-21 09:22:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'site_photo',
        '위험성평가_현장사진.jpg',
        'projects/pt-manufacturing/evidence/위험성평가_현장사진.jpg',
        'image/jpeg', 3145728, '2026-04-21 09:23:00+09'
    ),
    -- 세금내역서 + 제3자확인서
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'tax_invoice',
        '세금내역서_위험성평가_6월.pdf',
        'projects/pt-manufacturing/evidence/세금내역서_위험성평가_6월.pdf',
        'application/pdf', 358400, '2026-04-21 09:30:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM users WHERE employee_no = 'USR-201'),
        'tax_invoice_confirm',
        '제3자사실관계확인서_위험성평가_6월.pdf',
        'projects/pt-manufacturing/evidence/제3자사실관계확인서_위험성평가_6월.pdf',
        'application/pdf', 286720, '2026-04-21 09:30:30+09'
    );

-- ─────────────────────────────────────────────────────────────
-- 12. 사용내역서 (평택 4월, 3월)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statements (
    project_id, source_file_id, report_month, revision_no,
    document_written_date, cumulative_progress_rate
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM files WHERE storage_key = 'projects/pt-manufacturing/statements/평택_사용내역서_2026-04.xlsx'),
        '2026-04-01', 2, '2026-04-20', 91.00
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2024-0108'),
        (SELECT id FROM files WHERE storage_key = 'projects/pt-manufacturing/statements/평택_사용내역서_2026-03.xlsx'),
        '2026-03-01', 1, '2026-03-20', 88.00
    );

-- ─────────────────────────────────────────────────────────────
-- 13. 사용내역서 요약 (평택 4월)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statement_summaries (
    usage_statement_id, category_code, previous_amount, current_amount, cumulative_amount
)
SELECT
    us.id,
    v.category_code,
    v.previous_amount,
    v.current_amount,
    v.cumulative_amount
FROM usage_statements us
CROSS JOIN (VALUES
    ('CAT_01', 12000000::NUMERIC, 1800000::NUMERIC, 13800000::NUMERIC),
    ('CAT_02',  5500000::NUMERIC,  800000::NUMERIC,  6300000::NUMERIC),
    ('CAT_03',  2800000::NUMERIC,  320000::NUMERIC,  3120000::NUMERIC),
    ('CAT_04',  1000000::NUMERIC,  500000::NUMERIC,  1500000::NUMERIC),
    ('CAT_05',  3200000::NUMERIC, 1000000::NUMERIC,  4200000::NUMERIC),
    ('CAT_06',        0::NUMERIC,  500000::NUMERIC,   500000::NUMERIC),
    ('CAT_07',        0::NUMERIC,        0::NUMERIC,        0::NUMERIC),
    ('CAT_08',  1700000::NUMERIC,        0::NUMERIC,  1700000::NUMERIC)
) AS v(category_code, previous_amount, current_amount, cumulative_amount)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0108')
  AND us.report_month = '2026-04-01';

-- ─────────────────────────────────────────────────────────────
-- 14. 상세 항목 (평택 4월)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statement_items (
    usage_statement_id, category_code, used_on, item_name,
    unit, quantity, unit_price, total_amount, remark, page_no
)
SELECT
    us.id,
    v.category_code,
    v.used_on::DATE,
    v.item_name,
    v.unit,
    v.quantity,
    v.unit_price,
    v.total_amount,
    v.remark,
    v.page_no
FROM usage_statements us
CROSS JOIN (VALUES
    ('CAT_01', '2026-04-30', '보건관리자 임금 (4월)',     '월',  1, 1800000.00, 1800000, '보건관리 용역',   3),
    ('CAT_05', '2026-04-25', '근로자 건강검진 비용',       '명', 10,  100000.00, 1000000, '정기 건강검진',   7),
    ('CAT_06', '2026-04-18', '안전보건교육비',             '식',  1,   500000.00,  500000, '신규 입사자 교육', 9),
    ('CAT_04', '2026-04-15', '안전진단 용역비',            '식',  1,   500000.00,  500000, '1분기 정기 진단', 11),
    ('CAT_03', '2026-04-10', '보호구 구입',                '식',  1,   320000.00,  320000, '소모성 보호구',   13)
) AS v(category_code, used_on, item_name, unit, quantity, unit_price, total_amount, remark, page_no)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0108')
  AND us.report_month = '2026-04-01';

-- ─────────────────────────────────────────────────────────────
-- 15. 증빙 요건 (평택 - 모두 충족)
-- ─────────────────────────────────────────────────────────────
INSERT INTO evidence_requirements (usage_statement_item_id, evidence_type_code, is_satisfied)
SELECT item.id, req.evidence_type_code, true
FROM usage_statement_items item
JOIN usage_statements us ON item.usage_statement_id = us.id
CROSS JOIN (VALUES
    ('tax_invoice'),
    ('transaction_statement'),
    ('work_photo')
) AS req(evidence_type_code)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0108')
  AND us.report_month = '2026-04-01';

-- ─────────────────────────────────────────────────────────────
-- 16. 검증 로그 (평택 - 정상)
-- ─────────────────────────────────────────────────────────────
INSERT INTO agent_logs (
    project_id, usage_statement_id,
    agent_type_code, status_code, details, model_name, created_at
)
SELECT
    us.project_id,
    us.id,
    vd.agent_type_code,
    'success',
    vd.details::JSONB,
    'claude-sonnet-4-20250514',
    '2026-04-21 14:00:00+09'
FROM usage_statements us
CROSS JOIN (VALUES
    ('classi', '{"result":"pass","summary":"사용내역서 금액과 영수증 합계 일치","total_statement":4920000,"total_receipts":4920000}'),
    ('vision', '{"result":"pass","summary":"보건관리자 현장 방문 및 활동 사진 목적 적합성 확인"}'),
    ('legal',  '{"result":"pass","summary":"모든 항목 법령 기준 내 정산 적합"}')
) AS vd(agent_type_code, details)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2024-0108')
  AND us.report_month = '2026-04-01';

-- ═════════════════════════════════════════════════════════════
-- 광명 데이터센터 (업로드 대기 - 초기 시나리오)
-- 이전 차수(3월) 데이터만 존재, 4월은 업로드 미완료
-- ═════════════════════════════════════════════════════════════

-- ─────────────────────────────────────────────────────────────
-- 17. 파일 (광명 3월 - 이미 제출된 이전 차수)
-- ─────────────────────────────────────────────────────────────
INSERT INTO files (
    project_id, uploaded_by_user_id, uploaded_evidence_type_code,
    original_filename, storage_key, mime_type, size_bytes, uploaded_at
) VALUES
    (
        (SELECT id FROM projects WHERE contract_no = '2025-0016'),
        (SELECT id FROM users WHERE employee_no = 'USR-301'),
        'usage_statement',
        '광명_사용내역서_2026-03.xlsx',
        'projects/gm-datacenter/statements/광명_사용내역서_2026-03.xlsx',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        143360, '2026-03-19 09:00:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2025-0016'),
        (SELECT id FROM users WHERE employee_no = 'USR-301'),
        'transaction_statement',
        '안전관리비_3월_영수증_일부.pdf',
        'projects/gm-datacenter/evidence/안전관리비_3월_영수증_일부.pdf',
        'application/pdf', 245760, '2026-03-19 09:10:00+09'
    ),
    (
        (SELECT id FROM projects WHERE contract_no = '2025-0016'),
        (SELECT id FROM users WHERE employee_no = 'USR-301'),
        'site_photo',
        '현장_안전시설_3월.jpg',
        'projects/gm-datacenter/evidence/현장_안전시설_3월.jpg',
        'image/jpeg', 3145728, '2026-03-19 09:15:00+09'
    );

-- ─────────────────────────────────────────────────────────────
-- 18. 사용내역서 (광명 3월만 등록, 4월은 미등록)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statements (
    project_id, source_file_id, report_month, revision_no,
    document_written_date, cumulative_progress_rate
) VALUES (
    (SELECT id FROM projects WHERE contract_no = '2025-0016'),
    (SELECT id FROM files WHERE storage_key = 'projects/gm-datacenter/statements/광명_사용내역서_2026-03.xlsx'),
    '2026-03-01', 1, '2026-03-18', 12.00
);

-- ─────────────────────────────────────────────────────────────
-- 19. 사용내역서 요약 (광명 3월)
-- ─────────────────────────────────────────────────────────────
INSERT INTO usage_statement_summaries (
    usage_statement_id, category_code, previous_amount, current_amount, cumulative_amount
)
SELECT
    us.id,
    v.category_code,
    v.previous_amount,
    v.current_amount,
    v.cumulative_amount
FROM usage_statements us
CROSS JOIN (VALUES
    ('CAT_01', 5200000::NUMERIC, 1500000::NUMERIC, 6700000::NUMERIC),
    ('CAT_02', 1800000::NUMERIC,  620000::NUMERIC, 2420000::NUMERIC),
    ('CAT_03',  300000::NUMERIC,  100000::NUMERIC,  400000::NUMERIC),
    ('CAT_04',       0::NUMERIC,       0::NUMERIC,       0::NUMERIC),
    ('CAT_05',  200000::NUMERIC,  100000::NUMERIC,  300000::NUMERIC),
    ('CAT_06',       0::NUMERIC,       0::NUMERIC,       0::NUMERIC),
    ('CAT_07',       0::NUMERIC,       0::NUMERIC,       0::NUMERIC),
    ('CAT_08',       0::NUMERIC,       0::NUMERIC,       0::NUMERIC)
) AS v(category_code, previous_amount, current_amount, cumulative_amount)
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2025-0016')
  AND us.report_month = '2026-03-01';

-- ─────────────────────────────────────────────────────────────
-- 20. 검증 로그 (광명 3월 - 이슈 1건 검증 중)
-- ─────────────────────────────────────────────────────────────
INSERT INTO agent_logs (
    project_id, usage_statement_id,
    agent_type_code, status_code, details, model_name, created_at
)
SELECT
    us.project_id,
    us.id,
    'classi',
    'success',
    '{"result":"conditional","issue":"일부 영수증 금액 OCR 인식 오류 - 수동 확인 필요","affected_items":1}'::JSONB,
    'claude-sonnet-4-20250514',
    '2026-03-20 10:00:00+09'
FROM usage_statements us
WHERE us.project_id = (SELECT id FROM projects WHERE contract_no = '2025-0016')
  AND us.report_month = '2026-03-01';
