CREATE SCHEMA IF NOT EXISTS service;
SET LOCAL search_path TO service, public;

-- ─────────────────────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────────────────────

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    employee_no VARCHAR(50) NOT NULL,
    real_name VARCHAR(100) NOT NULL,
    password_hash TEXT NOT NULL,
    role_code VARCHAR(30) NOT NULL DEFAULT 'user',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_employee_no UNIQUE (employee_no),
    CONSTRAINT chk_users_role_code CHECK (role_code IN ('system_admin', 'admin', 'user', 'agent'))
);

CREATE TABLE usage_categories (
    code VARCHAR(50) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    CONSTRAINT uq_usage_categories_name UNIQUE (name)
);

CREATE TABLE evidence_types (
    code VARCHAR(30) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE projects (
    id BIGSERIAL PRIMARY KEY,
    contract_no VARCHAR(100),
    construction_company VARCHAR(200) NOT NULL,
    project_name VARCHAR(300) NOT NULL,
    site_location VARCHAR(500) NOT NULL,
    representative_name VARCHAR(100),
    contract_amount NUMERIC(18, 0) NOT NULL DEFAULT 0,
    construction_start_date DATE NOT NULL,
    construction_end_date DATE NOT NULL,
    client_name VARCHAR(200),
    appropriated_amount NUMERIC(18, 0) NOT NULL DEFAULT 0,
    project_status_code VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_projects_contract_amount_non_negative CHECK (contract_amount >= 0),
    CONSTRAINT chk_projects_appropriated_amount_non_negative CHECK (appropriated_amount >= 0),
    CONSTRAINT chk_projects_construction_date_range CHECK (construction_end_date >= construction_start_date),
    CONSTRAINT chk_projects_status_code CHECK (project_status_code IN ('active', 'completed', 'suspended'))
);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE project_user_assignments (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    assigned_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_project_user_assignments_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_user_assignments_project_id
        FOREIGN KEY (project_id) REFERENCES projects (id),
    CONSTRAINT fk_project_user_assignments_user_id
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_project_user_assignments_assigned_by_user_id
        FOREIGN KEY (assigned_by_user_id) REFERENCES users (id)
);

CREATE TABLE files (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    uploaded_evidence_type_code VARCHAR(30) NOT NULL,
    original_filename VARCHAR(500) NOT NULL,
    storage_key TEXT NOT NULL,
    mime_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT NOT NULL,
    captured_at TIMESTAMPTZ,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    status_code VARCHAR(20) NOT NULL DEFAULT 'draft',
    deleted_at TIMESTAMPTZ,
    deleted_by_user_id BIGINT,
    CONSTRAINT uq_files_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_files_size_bytes_non_negative CHECK (size_bytes >= 0),
    CONSTRAINT chk_files_status_code CHECK (status_code IN ('draft', 'success', 'fail'))
);

CREATE TABLE usage_statements (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    source_file_id BIGINT,
    report_month DATE NOT NULL,
    revision_no INTEGER NOT NULL DEFAULT 1,
    document_written_date DATE NOT NULL,
    cumulative_progress_rate NUMERIC(5, 2) NOT NULL DEFAULT 0,
    status_code VARCHAR(30) NOT NULL DEFAULT 'draft',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_usage_statements_source_file_id UNIQUE (source_file_id),
    CONSTRAINT uq_usage_statements_project_month_revision UNIQUE (project_id, report_month, revision_no),
    CONSTRAINT chk_usage_statements_revision_no_positive CHECK (revision_no >= 1),
    CONSTRAINT chk_usage_statements_report_month_first_day CHECK (report_month = date_trunc('month', report_month)::date),
    CONSTRAINT chk_usage_statements_progress_rate CHECK (cumulative_progress_rate BETWEEN 0 AND 100),
    CONSTRAINT chk_usage_statements_status_code CHECK (status_code IN ('draft', 'upload_completed', 'supplement_required', 'review_completed'))
);

CREATE TABLE usage_statement_summaries (
    id BIGSERIAL PRIMARY KEY,
    usage_statement_id BIGINT NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    previous_amount NUMERIC(18, 0) NOT NULL DEFAULT 0,
    current_amount NUMERIC(18, 0) NOT NULL DEFAULT 0,
    cumulative_amount NUMERIC(18, 0) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_usage_statement_summaries_statement_category UNIQUE (usage_statement_id, category_code),
    CONSTRAINT chk_usage_statement_summaries_previous_amount_non_negative CHECK (previous_amount >= 0),
    CONSTRAINT chk_usage_statement_summaries_current_amount_non_negative CHECK (current_amount >= 0),
    CONSTRAINT chk_usage_statement_summaries_cumulative_amount_non_negative CHECK (cumulative_amount >= 0)
);

CREATE TABLE usage_statement_items (
    id BIGSERIAL PRIMARY KEY,
    usage_statement_id BIGINT NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    used_on DATE NOT NULL,
    item_name VARCHAR(300) NOT NULL,
    unit VARCHAR(50),
    quantity NUMERIC(14, 3) NOT NULL DEFAULT 0,
    unit_price NUMERIC(18, 2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18, 0) NOT NULL DEFAULT 0,
    remark VARCHAR(1000),
    page_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_usage_statement_items_quantity_non_negative CHECK (quantity >= 0),
    CONSTRAINT chk_usage_statement_items_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT chk_usage_statement_items_total_amount_non_negative CHECK (total_amount >= 0),
    CONSTRAINT chk_usage_statement_items_page_no_positive CHECK (page_no >= 1)
);

CREATE TABLE evidence_file_links (
    id BIGSERIAL PRIMARY KEY,
    usage_statement_item_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    evidence_type_code VARCHAR(30) NOT NULL,
    checked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_evidence_file_links_item_file UNIQUE (usage_statement_item_id, file_id)
);

CREATE TABLE evidence_requirements (
    id BIGSERIAL PRIMARY KEY,
    usage_statement_item_id BIGINT NOT NULL,
    evidence_type_code VARCHAR(30) NOT NULL,
    is_satisfied BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent_logs (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    usage_statement_id BIGINT,
    usage_statement_item_id BIGINT,
    agent_type_code VARCHAR(20) NOT NULL,
    status_code VARCHAR(20) NOT NULL DEFAULT 'pending',
    result_code VARCHAR(20),
    reason TEXT,
    details JSONB,
    model_name VARCHAR(100),
    token BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_agent_logs_agent_type_code CHECK (agent_type_code IN ('classi', 'safety-doc', 'link', 'vision', 'legal', 'report', 'orchestrator')),
    CONSTRAINT chk_agent_logs_status_code CHECK (status_code IN ('pending', 'running', 'success', 'fail', 'canceled')),
    CONSTRAINT chk_agent_logs_result_code CHECK (result_code IN ('success', 'hil', 'fail'))
);

CREATE TABLE action_requests (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL,
    usage_statement_id BIGINT,
    usage_statement_item_id BIGINT,
    requested_by_user_id BIGINT NOT NULL,
    assignee_user_id BIGINT,
    title VARCHAR(300) NOT NULL,
    reason TEXT,
    status_code VARCHAR(30) NOT NULL,
    due_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    closed_at TIMESTAMPTZ,
    CONSTRAINT chk_action_requests_status_code CHECK (status_code IN ('open', 'in_progress', 'closed')),
    CONSTRAINT chk_action_requests_closed_at_required CHECK (
        (status_code = 'closed' AND closed_at IS NOT NULL)
        OR status_code <> 'closed'
    )
);

-- ─────────────────────────────────────────────────────────────
-- Foreign keys
-- ─────────────────────────────────────────────────────────────

ALTER TABLE files
    ADD CONSTRAINT fk_files_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE files
    ADD CONSTRAINT fk_files_uploaded_by_user_id
    FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id);

ALTER TABLE files
    ADD CONSTRAINT fk_files_uploaded_evidence_type_code
    FOREIGN KEY (uploaded_evidence_type_code) REFERENCES evidence_types (code);

ALTER TABLE files
    ADD CONSTRAINT fk_files_deleted_by_user_id
    FOREIGN KEY (deleted_by_user_id) REFERENCES users (id);

ALTER TABLE usage_statements
    ADD CONSTRAINT fk_usage_statements_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE usage_statements
    ADD CONSTRAINT fk_usage_statements_source_file_id
    FOREIGN KEY (source_file_id) REFERENCES files (id);

ALTER TABLE usage_statement_summaries
    ADD CONSTRAINT fk_usage_statement_summaries_usage_statement_id
    FOREIGN KEY (usage_statement_id) REFERENCES usage_statements (id);

ALTER TABLE usage_statement_summaries
    ADD CONSTRAINT fk_usage_statement_summaries_category_code
    FOREIGN KEY (category_code) REFERENCES usage_categories (code);

ALTER TABLE usage_statement_items
    ADD CONSTRAINT fk_usage_statement_items_usage_statement_id
    FOREIGN KEY (usage_statement_id) REFERENCES usage_statements (id);

ALTER TABLE usage_statement_items
    ADD CONSTRAINT fk_usage_statement_items_category_code
    FOREIGN KEY (category_code) REFERENCES usage_categories (code);

ALTER TABLE evidence_file_links
    ADD CONSTRAINT fk_evidence_file_links_usage_statement_item_id
    FOREIGN KEY (usage_statement_item_id) REFERENCES usage_statement_items (id);

ALTER TABLE evidence_file_links
    ADD CONSTRAINT fk_evidence_file_links_file_id
    FOREIGN KEY (file_id) REFERENCES files (id);

ALTER TABLE evidence_file_links
    ADD CONSTRAINT fk_evidence_file_links_evidence_type_code
    FOREIGN KEY (evidence_type_code) REFERENCES evidence_types (code);

ALTER TABLE evidence_requirements
    ADD CONSTRAINT fk_evidence_requirements_usage_statement_item_id
    FOREIGN KEY (usage_statement_item_id) REFERENCES usage_statement_items (id);

ALTER TABLE evidence_requirements
    ADD CONSTRAINT fk_evidence_requirements_evidence_type_code
    FOREIGN KEY (evidence_type_code) REFERENCES evidence_types (code);

ALTER TABLE agent_logs
    ADD CONSTRAINT fk_agent_logs_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE agent_logs
    ADD CONSTRAINT fk_agent_logs_usage_statement_id
    FOREIGN KEY (usage_statement_id) REFERENCES usage_statements (id);

ALTER TABLE agent_logs
    ADD CONSTRAINT fk_agent_logs_usage_statement_item_id
    FOREIGN KEY (usage_statement_item_id) REFERENCES usage_statement_items (id);

ALTER TABLE action_requests
    ADD CONSTRAINT fk_action_requests_project_id
    FOREIGN KEY (project_id) REFERENCES projects (id);

ALTER TABLE action_requests
    ADD CONSTRAINT fk_action_requests_usage_statement_id
    FOREIGN KEY (usage_statement_id) REFERENCES usage_statements (id);

ALTER TABLE action_requests
    ADD CONSTRAINT fk_action_requests_usage_statement_item_id
    FOREIGN KEY (usage_statement_item_id) REFERENCES usage_statement_items (id);

ALTER TABLE action_requests
    ADD CONSTRAINT fk_action_requests_requested_by_user_id
    FOREIGN KEY (requested_by_user_id) REFERENCES users (id);

ALTER TABLE action_requests
    ADD CONSTRAINT fk_action_requests_assignee_user_id
    FOREIGN KEY (assignee_user_id) REFERENCES users (id);

-- ─────────────────────────────────────────────────────────────
-- Indexes
-- ─────────────────────────────────────────────────────────────

CREATE INDEX idx_projects_status_created_at ON projects (project_status_code, created_at DESC);
CREATE INDEX idx_projects_created_at ON projects (created_at DESC);
CREATE INDEX idx_projects_contract_no ON projects (contract_no);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_active_user ON refresh_tokens (user_id, expires_at) WHERE revoked_at IS NULL;

CREATE INDEX idx_project_user_assignments_user_project ON project_user_assignments (user_id, project_id);
CREATE INDEX idx_project_user_assignments_project_id ON project_user_assignments (project_id);
CREATE INDEX idx_project_user_assignments_assigned_by_user_id ON project_user_assignments (assigned_by_user_id) WHERE assigned_by_user_id IS NOT NULL;

CREATE INDEX idx_files_project_uploaded_at ON files (project_id, uploaded_at DESC);
CREATE INDEX idx_files_uploaded_by_user_id ON files (uploaded_by_user_id);
CREATE INDEX idx_files_uploaded_evidence_type_uploaded_at ON files (uploaded_evidence_type_code, uploaded_at DESC);
CREATE INDEX idx_files_captured_at ON files (captured_at) WHERE captured_at IS NOT NULL;
CREATE INDEX idx_files_project_active_uploaded_at ON files (project_id, uploaded_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_deleted_by_user_id ON files (deleted_by_user_id) WHERE deleted_by_user_id IS NOT NULL;

CREATE INDEX idx_usage_statements_project_id ON usage_statements (project_id);
CREATE INDEX idx_usage_statements_report_month ON usage_statements (report_month DESC);
CREATE INDEX idx_usage_statements_status_code ON usage_statements (status_code);

CREATE INDEX idx_usage_statement_summaries_category_code ON usage_statement_summaries (category_code);

CREATE INDEX idx_usage_statement_items_statement_date ON usage_statement_items (usage_statement_id, used_on DESC);
CREATE INDEX idx_usage_statement_items_statement_category ON usage_statement_items (usage_statement_id, category_code);
CREATE INDEX idx_usage_statement_items_category_date ON usage_statement_items (category_code, used_on DESC);

CREATE INDEX idx_evidence_file_links_evidence_type_code ON evidence_file_links (evidence_type_code);
CREATE INDEX idx_evidence_file_links_checked_at ON evidence_file_links (checked_at) WHERE checked_at IS NOT NULL;
CREATE INDEX idx_evidence_file_links_unchecked ON evidence_file_links (usage_statement_item_id, file_id) WHERE checked_at IS NULL;

CREATE UNIQUE INDEX uq_evidence_requirements_active_item_type
    ON evidence_requirements (usage_statement_item_id, evidence_type_code)
    WHERE is_active = true;

CREATE INDEX idx_evidence_requirements_active_unsatisfied
    ON evidence_requirements (usage_statement_item_id, evidence_type_code)
    WHERE is_active = true AND is_satisfied = false;

CREATE INDEX idx_evidence_requirements_evidence_type_code ON evidence_requirements (evidence_type_code);

CREATE INDEX idx_agent_logs_project_created_at ON agent_logs (project_id, created_at DESC);
CREATE INDEX idx_agent_logs_statement_created_at ON agent_logs (usage_statement_id, created_at DESC)
    WHERE usage_statement_id IS NOT NULL;
CREATE INDEX idx_agent_logs_type_status_created_at ON agent_logs (agent_type_code, status_code, created_at DESC);
CREATE INDEX idx_agent_logs_details_gin ON agent_logs USING GIN (details);
CREATE INDEX idx_agent_logs_result_code ON agent_logs (result_code) WHERE result_code IS NOT NULL;
CREATE INDEX idx_agent_logs_item_id ON agent_logs (usage_statement_item_id) WHERE usage_statement_item_id IS NOT NULL;

CREATE INDEX idx_action_requests_project_status ON action_requests (project_id, status_code);
CREATE INDEX idx_action_requests_assignee_status ON action_requests (assignee_user_id, status_code) WHERE assignee_user_id IS NOT NULL;
CREATE INDEX idx_action_requests_requested_by_user_id ON action_requests (requested_by_user_id);
CREATE INDEX idx_action_requests_statement_id ON action_requests (usage_statement_id) WHERE usage_statement_id IS NOT NULL;
CREATE INDEX idx_action_requests_item_id ON action_requests (usage_statement_item_id) WHERE usage_statement_item_id IS NOT NULL;
CREATE INDEX idx_action_requests_open_due ON action_requests (project_id, due_date) WHERE status_code IN ('open', 'in_progress') AND due_date IS NOT NULL;

-- ─────────────────────────────────────────────────────────────
-- updated_at trigger
-- ─────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_updated_at
BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_projects_set_updated_at
BEFORE UPDATE ON projects FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_usage_statements_set_updated_at
BEFORE UPDATE ON usage_statements FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_usage_statement_summaries_set_updated_at
BEFORE UPDATE ON usage_statement_summaries FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_usage_statement_items_set_updated_at
BEFORE UPDATE ON usage_statement_items FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_evidence_file_links_set_updated_at
BEFORE UPDATE ON evidence_file_links FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_evidence_requirements_set_updated_at
BEFORE UPDATE ON evidence_requirements FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_agent_logs_set_updated_at
BEFORE UPDATE ON agent_logs FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ─────────────────────────────────────────────────────────────
-- Column comments
-- ─────────────────────────────────────────────────────────────

COMMENT ON COLUMN users.id IS '사용자ID';
COMMENT ON COLUMN users.employee_no IS '로그인용 사번';
COMMENT ON COLUMN users.real_name IS '실제 이름';
COMMENT ON COLUMN users.password_hash IS '비밀번호해시';
COMMENT ON COLUMN users.role_code IS '권한 (system_admin/admin/user/agent)';
COMMENT ON COLUMN users.created_at IS '생성일시';
COMMENT ON COLUMN users.updated_at IS '수정일시';

COMMENT ON COLUMN projects.id IS '프로젝트ID';
COMMENT ON COLUMN projects.construction_company IS '건설업체';
COMMENT ON COLUMN projects.project_name IS '공사명';
COMMENT ON COLUMN projects.site_location IS '소재지';
COMMENT ON COLUMN projects.representative_name IS '대표자';
COMMENT ON COLUMN projects.contract_amount IS '공사금액';
COMMENT ON COLUMN projects.construction_start_date IS '공사시작일';
COMMENT ON COLUMN projects.construction_end_date IS '공사종료일';
COMMENT ON COLUMN projects.client_name IS '발주자';
COMMENT ON COLUMN projects.appropriated_amount IS '계상금액';
COMMENT ON COLUMN projects.project_status_code IS '프로젝트진행상태 (active/completed/suspended)';
COMMENT ON COLUMN projects.created_at IS '생성일시';
COMMENT ON COLUMN projects.updated_at IS '수정일시';

COMMENT ON COLUMN usage_categories.code IS '카테고리코드';
COMMENT ON COLUMN usage_categories.name IS '카테고리명';

COMMENT ON COLUMN usage_statements.id IS '사용내역서ID';
COMMENT ON COLUMN usage_statements.project_id IS '프로젝트ID';
COMMENT ON COLUMN usage_statements.source_file_id IS '원본PDF파일ID';
COMMENT ON COLUMN usage_statements.report_month IS '보고월 (해당월 1일로 저장)';
COMMENT ON COLUMN usage_statements.revision_no IS '개정번호';
COMMENT ON COLUMN usage_statements.document_written_date IS '문서작성일';
COMMENT ON COLUMN usage_statements.cumulative_progress_rate IS '누계공정률';
COMMENT ON COLUMN usage_statements.status_code IS '사용내역서 제출·검토 단계 (draft → upload_completed → supplement_required → review_completed)';
COMMENT ON COLUMN usage_statements.created_at IS '생성일시';
COMMENT ON COLUMN usage_statements.updated_at IS '수정일시';

COMMENT ON COLUMN usage_statement_summaries.id IS '요약항목ID';
COMMENT ON COLUMN usage_statement_summaries.usage_statement_id IS '사용내역서ID';
COMMENT ON COLUMN usage_statement_summaries.category_code IS '카테고리코드';
COMMENT ON COLUMN usage_statement_summaries.previous_amount IS '전회금액';
COMMENT ON COLUMN usage_statement_summaries.current_amount IS '금회금액';
COMMENT ON COLUMN usage_statement_summaries.cumulative_amount IS '누계금액';
COMMENT ON COLUMN usage_statement_summaries.created_at IS '생성일시';
COMMENT ON COLUMN usage_statement_summaries.updated_at IS '수정일시';

COMMENT ON COLUMN usage_statement_items.id IS '상세항목ID';
COMMENT ON COLUMN usage_statement_items.usage_statement_id IS '사용내역서ID';
COMMENT ON COLUMN usage_statement_items.category_code IS '카테고리코드';
COMMENT ON COLUMN usage_statement_items.used_on IS '사용일자';
COMMENT ON COLUMN usage_statement_items.item_name IS '품목';
COMMENT ON COLUMN usage_statement_items.unit IS '단위';
COMMENT ON COLUMN usage_statement_items.quantity IS '수량';
COMMENT ON COLUMN usage_statement_items.unit_price IS '단가';
COMMENT ON COLUMN usage_statement_items.total_amount IS '합계';
COMMENT ON COLUMN usage_statement_items.remark IS '비고';
COMMENT ON COLUMN usage_statement_items.page_no IS '페이지번호';
COMMENT ON COLUMN usage_statement_items.created_at IS '생성일시';
COMMENT ON COLUMN usage_statement_items.updated_at IS '수정일시';

COMMENT ON COLUMN files.id IS '파일ID';
COMMENT ON COLUMN files.project_id IS '어느 프로젝트에 올렸나';
COMMENT ON COLUMN files.uploaded_by_user_id IS '업로드사용자ID';
COMMENT ON COLUMN files.uploaded_evidence_type_code IS '유저가업로드할때선택한증빙유형코드';
COMMENT ON COLUMN files.original_filename IS '원본파일명';
COMMENT ON COLUMN files.storage_key IS '저장경로 (S3 key 등)';
COMMENT ON COLUMN files.mime_type IS 'MIME유형';
COMMENT ON COLUMN files.size_bytes IS '파일크기(bytes)';
COMMENT ON COLUMN files.captured_at IS '촬영일시 (현장사진인 경우 EXIF 등)';
COMMENT ON COLUMN files.uploaded_at IS '업로드일시';
COMMENT ON COLUMN files.deleted_at IS '소프트삭제일시';
COMMENT ON COLUMN files.deleted_by_user_id IS '삭제한사용자ID';

COMMENT ON COLUMN evidence_file_links.id IS '매핑ID';
COMMENT ON COLUMN evidence_file_links.usage_statement_item_id IS '증빙 대상 상세항목ID';
COMMENT ON COLUMN evidence_file_links.file_id IS '증빙 파일ID';
COMMENT ON COLUMN evidence_file_links.evidence_type_code IS '증빙유형 (receipt / site_photo / etc)';
COMMENT ON COLUMN evidence_file_links.checked_at IS '사용자 확인 일시';
COMMENT ON COLUMN evidence_file_links.created_at IS '생성일시';
COMMENT ON COLUMN evidence_file_links.updated_at IS '수정일시';

COMMENT ON COLUMN evidence_requirements.evidence_type_code IS 'receipt / site_photo / etc';
COMMENT ON COLUMN evidence_requirements.is_satisfied IS '제출완료여부';
COMMENT ON COLUMN evidence_requirements.is_active IS 'Agent 재실행 시 무효화 플래그';

COMMENT ON COLUMN evidence_types.code IS '증빙유형코드 (receipt / site_photo / etc)';
COMMENT ON COLUMN evidence_types.name IS '증빙유형명';
COMMENT ON COLUMN evidence_types.description IS '설명';

COMMENT ON COLUMN agent_logs.id IS 'agent 로그 ID';
COMMENT ON COLUMN agent_logs.agent_type_code IS '실행한 에이전트 코드 (classi / safety-doc / link / vision / legal / report / orchestrator)';
COMMENT ON COLUMN agent_logs.status_code IS '실행 상태 (pending / running / success / fail / canceled)';
COMMENT ON COLUMN agent_logs.result_code IS '판단 결과 (success / hil / fail) — status=success일 때만 유효';
COMMENT ON COLUMN agent_logs.reason IS '프론트 표시용 한 줄 사유';
COMMENT ON COLUMN agent_logs.details IS '에이전트별 추가 페이로드 JSONB (issue_type, 수치 등)';
COMMENT ON COLUMN agent_logs.model_name IS '사용된 AI 모델명';
COMMENT ON COLUMN agent_logs.token IS '사용 토큰 수';
COMMENT ON COLUMN agent_logs.usage_statement_item_id IS '상세항목ID — 항목 기준 1 row. legal·report는 NULL.';
COMMENT ON COLUMN agent_logs.updated_at IS '최종 수정일시 (update-in-place 갱신 추적용)';

COMMENT ON COLUMN action_requests.id IS '액션요청ID';
COMMENT ON COLUMN action_requests.project_id IS '프로젝트ID';
COMMENT ON COLUMN action_requests.usage_statement_id IS '관련 사용내역서ID (있는 경우)';
COMMENT ON COLUMN action_requests.usage_statement_item_id IS '관련 상세항목ID (있는 경우)';
COMMENT ON COLUMN action_requests.requested_by_user_id IS '요청자ID';
COMMENT ON COLUMN action_requests.assignee_user_id IS '담당자ID';
COMMENT ON COLUMN action_requests.title IS '요청제목';
COMMENT ON COLUMN action_requests.reason IS '요청사유';
COMMENT ON COLUMN action_requests.status_code IS '처리 상태 (open: 요청 발송됨 / in_progress: user 처리 중 / closed: admin 최종 확인 완료)';
COMMENT ON COLUMN action_requests.due_date IS '처리기한';
COMMENT ON COLUMN action_requests.created_at IS '생성일시';
COMMENT ON COLUMN action_requests.closed_at IS 'admin 최종 확인 완료 일시 (status_code = ''closed'' 전환 시 자동 설정)';
