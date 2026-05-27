SET search_path TO service, public;

-- safety_doc_agent가 item 1건의 필수 증빙을 판단할 때 사용하는 조회용 뷰

CREATE OR REPLACE VIEW service.v_ai_evidence_requirement_item_context AS
SELECT
    p.id                    AS project_id,
    p.project_name,
    usi.id                  AS item_id,
    usi.usage_statement_id,
    us.report_month,
    us.revision_no,
    usi.category_code,
    uc.name                 AS category_name,
    usi.used_on,
    usi.item_name,
    usi.unit,
    usi.quantity,
    usi.unit_price,
    usi.total_amount,
    usi.remark,
    usi.page_no
FROM service.usage_statement_items usi
JOIN service.usage_statements us
  ON us.id = usi.usage_statement_id
JOIN service.projects p
  ON p.id = us.project_id
JOIN service.usage_categories uc
  ON uc.code = usi.category_code;


-- 삭제된 파일(deleted_at IS NOT NULL) 제외
CREATE OR REPLACE VIEW service.v_ai_evidence_requirement_file_context AS
SELECT
    efl.usage_statement_item_id    AS item_id,
    f.id                           AS file_id,
    f.original_filename,
    f.mime_type,
    f.uploaded_evidence_type_code,
    efl.evidence_type_code         AS linked_evidence_type_code,
    f.storage_key,
    f.captured_at,
    f.uploaded_at
FROM service.evidence_file_links efl
JOIN service.files f
  ON f.id = efl.file_id
 AND f.deleted_at IS NULL;


CREATE OR REPLACE VIEW service.v_ai_evidence_requirement_targets AS
SELECT
    ctx.project_id,
    ctx.project_name,
    ctx.usage_statement_id,
    ctx.report_month,
    ctx.revision_no,
    ctx.item_id,
    ctx.category_code,
    ctx.category_name,
    ctx.item_name,
    ctx.used_on,
    ctx.total_amount,
    COALESCE(req.active_requirement_count, 0) AS active_requirement_count,
    COALESCE(linked.linked_file_count, 0)     AS linked_file_count
FROM service.v_ai_evidence_requirement_item_context ctx
LEFT JOIN (
    SELECT usage_statement_item_id,
           COUNT(*) AS active_requirement_count
    FROM service.evidence_requirements
    WHERE is_active = true
    GROUP BY usage_statement_item_id
) req ON req.usage_statement_item_id = ctx.item_id
LEFT JOIN (
    SELECT usage_statement_item_id,
           COUNT(*) AS linked_file_count
    FROM service.evidence_file_links
    GROUP BY usage_statement_item_id
) linked ON linked.usage_statement_item_id = ctx.item_id;


-- classifier_agent / validator_agent / OCR matching 공용
-- usage_statement_id 하나로 프로젝트 기본정보 + 상세항목 전부 조회
--
-- usage_statement_items를 LEFT JOIN으로 처리하는 이유:
-- FastAPI가 OCR → classifier → items INSERT 순서로 동작하므로
-- classifier가 이 뷰를 읽는 시점에 items가 아직 존재하지 않는다.
-- INNER JOIN이면 프로젝트 기본정보도 사라지므로 LEFT JOIN이 필수.
-- items가 없으면 item 관련 컬럼은 NULL로 반환된다.
CREATE OR REPLACE VIEW service.v_usage_statement_context AS
SELECT
    us.id                       AS usage_statement_id,
    us.project_id,
    us.report_month,
    us.cumulative_progress_rate,
    us.document_written_date,
    p.project_name,
    p.construction_company,
    p.contract_amount,
    p.appropriated_amount,
    p.construction_start_date,
    p.construction_end_date,
    p.site_location,
    p.client_name,
    usi.id                      AS item_id,
    usi.category_code,
    uc.name                     AS category_name,
    usi.used_on,
    usi.item_name,
    usi.unit,
    usi.quantity,
    usi.unit_price,
    usi.total_amount,
    usi.remark
FROM service.usage_statements us
JOIN service.projects p
  ON p.id = us.project_id
LEFT JOIN service.usage_statement_items usi
  ON usi.usage_statement_id = us.id
LEFT JOIN service.usage_categories uc
  ON uc.code = usi.category_code;
