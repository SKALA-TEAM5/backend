-- -----------------------------------------------------------------------------
-- Schema separation in a single RDB (PostgreSQL)
--   - legal_rag : legal data + RAG-oriented tables
--
-- 테이블 구성 (3개)
--   1. legal_master       : 법령 corpus + rule 통합 반정규화 마스터
--   2. legal_rule_profiles: 운영형 보강 룰 (synonym, allow/disallow terms)
--   3. law_log            : 새벽 배치 실행 단위 변경 이력
-- -----------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS legal_rag;

SET LOCAL search_path TO legal_rag, public;

CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- -----------------------------------------------------------------------------
-- 1. 통합 법령 마스터
--    corpus(조문·별표·Q&A)와 rule(허용/불가/한도 등)을 record_type으로 구분
--    hash 컬럼으로 새벽 배치 실행 시 청크 단위 변경 감지
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS legal_master (
    id              TEXT        PRIMARY KEY,

    -- 소스 메타
    source_name     TEXT        NOT NULL,
    source_type     TEXT        NOT NULL,
    source_path     TEXT        NOT NULL,

    -- 조문 계층
    article_no      TEXT,
    paragraph_no    TEXT,
    item_no         TEXT,
    section_path    TEXT,

    -- 벡터 DB 연결
    chunk_id        TEXT,

    -- 본문
    body            TEXT        NOT NULL,

    -- 레코드 구분
    record_type     TEXT        NOT NULL,    -- 'corpus' | 'rule'
    content_type    TEXT,                   -- corpus: article | appendix | qa | guideline
    rule_type       TEXT,                   -- rule: allowed | disallowed | limit | progress | category | qa

    -- 규칙 전용
    category_code   TEXT,
    category_name   TEXT,
    allowed         BOOLEAN,
    limit_pct       NUMERIC(6, 4),
    keyword         TEXT,
    item_pattern    TEXT,
    legal_basis     TEXT,

    -- 검색 보조
    cited_laws      TEXT[],
    keywords        TEXT[],

    -- 변경 감지
    hash            TEXT        NOT NULL,

    -- 공통
    metadata        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_legal_master_source_type
        CHECK (source_type IN ('law', 'guideline', 'qa')),

    CONSTRAINT chk_legal_master_record_type
        CHECK (record_type IN ('corpus', 'rule')),

    CONSTRAINT chk_legal_master_content_type
        CHECK (content_type IS NULL OR content_type IN ('article', 'appendix', 'qa', 'guideline')),

    CONSTRAINT chk_legal_master_rule_type
        CHECK (rule_type IS NULL OR rule_type IN ('allowed', 'disallowed', 'limit', 'progress', 'category', 'qa')),

    CONSTRAINT chk_legal_master_limit_pct
        CHECK (limit_pct IS NULL OR (limit_pct >= 0 AND limit_pct <= 1))
);

COMMENT ON TABLE  legal_master IS '통합 법령 마스터 — corpus + rule 단일 테이블, hash로 청크 단위 변경 감지';
COMMENT ON COLUMN legal_master.id           IS '레코드 고유 ID';
COMMENT ON COLUMN legal_master.source_name  IS '법령명 (예: 건설업 산업안전보건관리비 계상 및 사용기준)';
COMMENT ON COLUMN legal_master.source_type  IS '소스 유형 (law / guideline / qa)';
COMMENT ON COLUMN legal_master.source_path  IS '원본 URL 또는 파일 경로';
COMMENT ON COLUMN legal_master.article_no   IS '조 (예: 제7조)';
COMMENT ON COLUMN legal_master.paragraph_no IS '항 (예: 제1항)';
COMMENT ON COLUMN legal_master.item_no      IS '호 (예: 제2호)';
COMMENT ON COLUMN legal_master.section_path IS '계층 경로 (예: 제7조 > 제1항 > 제2호)';
COMMENT ON COLUMN legal_master.chunk_id     IS 'Qdrant 벡터 청크 연결 ID';
COMMENT ON COLUMN legal_master.body         IS '법령 본문 또는 규칙 원문';
COMMENT ON COLUMN legal_master.record_type  IS '레코드 유형 (corpus / rule)';
COMMENT ON COLUMN legal_master.content_type IS '본문 유형 — corpus일 때만 사용 (article / appendix / qa / guideline)';
COMMENT ON COLUMN legal_master.rule_type    IS '규칙 유형 — rule일 때만 사용 (allowed / disallowed / limit / progress / category / qa)';
COMMENT ON COLUMN legal_master.category_code IS '카테고리 코드 (CAT_01 ~ CAT_09)';
COMMENT ON COLUMN legal_master.category_name IS '카테고리 한글명';
COMMENT ON COLUMN legal_master.allowed      IS '허용 여부 (true=허용 / false=불가 / null=조건부)';
COMMENT ON COLUMN legal_master.limit_pct    IS '법령상 한도 비율 (예: 0.2 = 20%)';
COMMENT ON COLUMN legal_master.keyword      IS '매칭용 핵심 키워드';
COMMENT ON COLUMN legal_master.item_pattern IS '품목명 패턴 (정규식 또는 키워드)';
COMMENT ON COLUMN legal_master.legal_basis  IS '법적 근거 조항 (예: 제7조제1항제2호)';
COMMENT ON COLUMN legal_master.cited_laws   IS '본문 내 인용 법령 조항 목록';
COMMENT ON COLUMN legal_master.keywords     IS 'RDB 매칭용 키워드 배열';
COMMENT ON COLUMN legal_master.hash         IS '본문 hash — 새벽 배치에서 변경 감지 기준';
COMMENT ON COLUMN legal_master.metadata     IS '추가 메타데이터';
COMMENT ON COLUMN legal_master.created_at   IS '최초 적재 일시';

-- 조회 인덱스
CREATE INDEX IF NOT EXISTS idx_legal_master_record_type
    ON legal_master (record_type);

CREATE INDEX IF NOT EXISTS idx_legal_master_source_name
    ON legal_master (source_name);

CREATE INDEX IF NOT EXISTS idx_legal_master_source_type
    ON legal_master (source_type);

CREATE INDEX IF NOT EXISTS idx_legal_master_article_no
    ON legal_master (article_no)
    WHERE article_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_category_code
    ON legal_master (category_code)
    WHERE category_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_rule_type
    ON legal_master (rule_type)
    WHERE rule_type IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_legal_basis
    ON legal_master (legal_basis)
    WHERE legal_basis IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_record_category
    ON legal_master (record_type, category_code)
    WHERE category_code IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_chunk_id
    ON legal_master (chunk_id)
    WHERE chunk_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_hash
    ON legal_master (hash);

-- 전문 검색 인덱스 (trigram)
CREATE INDEX IF NOT EXISTS idx_legal_master_body_trgm
    ON legal_master USING gin (body gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_legal_master_keyword_trgm
    ON legal_master USING gin (keyword gin_trgm_ops)
    WHERE keyword IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_item_pattern_trgm
    ON legal_master USING gin (item_pattern gin_trgm_ops)
    WHERE item_pattern IS NOT NULL;

-- 배열 검색 인덱스
CREATE INDEX IF NOT EXISTS idx_legal_master_cited_laws_gin
    ON legal_master USING gin (cited_laws)
    WHERE cited_laws IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_keywords_gin
    ON legal_master USING gin (keywords)
    WHERE keywords IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_legal_master_metadata_gin
    ON legal_master USING gin (metadata jsonb_path_ops);


-- -----------------------------------------------------------------------------
-- 2. 법령 규칙 프로파일
--    법령 소스와 무관한 운영형 보강 룰
--    (synonym, allow_terms, disallow_terms 등 수시 수정 대상)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS legal_rule_profiles (
    profile_id    TEXT        PRIMARY KEY,
    profile_scope TEXT        NOT NULL,
    category_code TEXT,
    profile_key   TEXT        NOT NULL,
    values_json   JSONB       NOT NULL,
    metadata      JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_legal_rule_profiles_profile_scope
        CHECK (profile_scope IN ('category', 'global', 'item')),

    CONSTRAINT chk_legal_rule_profiles_global_category
        CHECK ((profile_scope = 'global' AND category_code IS NULL) OR profile_scope <> 'global'),

    CONSTRAINT uq_legal_rule_profiles_scope_category_key
        UNIQUE (profile_scope, category_code, profile_key)
);

COMMENT ON TABLE  legal_rule_profiles IS '운영형 보강 룰 — 법령 소스와 무관하게 수시 수정';
COMMENT ON COLUMN legal_rule_profiles.profile_id    IS '프로파일 고유 ID';
COMMENT ON COLUMN legal_rule_profiles.profile_scope IS '적용 범위 (category / global / item)';
COMMENT ON COLUMN legal_rule_profiles.category_code IS '카테고리 코드 (global이면 null)';
COMMENT ON COLUMN legal_rule_profiles.profile_key   IS '프로파일 키 (예: allowed_keywords / disallowed_patterns)';
COMMENT ON COLUMN legal_rule_profiles.values_json   IS '프로파일 값 목록 (JSON 배열 또는 객체)';
COMMENT ON COLUMN legal_rule_profiles.metadata      IS '추가 메타데이터';
COMMENT ON COLUMN legal_rule_profiles.created_at    IS '생성 일시';

CREATE INDEX IF NOT EXISTS idx_legal_rule_profiles_scope_category
    ON legal_rule_profiles (profile_scope, category_code);

CREATE INDEX IF NOT EXISTS idx_legal_rule_profiles_profile_key
    ON legal_rule_profiles (profile_key);

CREATE INDEX IF NOT EXISTS idx_legal_rule_profiles_values_json_gin
    ON legal_rule_profiles USING gin (values_json jsonb_path_ops);

CREATE INDEX IF NOT EXISTS idx_legal_rule_profiles_metadata_gin
    ON legal_rule_profiles USING gin (metadata jsonb_path_ops);


-- -----------------------------------------------------------------------------
-- 3. 법령 변경 이력 로그
--    새벽 배치가 실행될 때마다 변경된 청크를 run_id 단위로 기록
--    change_type: added / updated / deleted
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS law_log (
    log_id       TEXT        PRIMARY KEY,
    run_id       TEXT        NOT NULL,
    master_id    TEXT,
    source_name  TEXT        NOT NULL,
    article_no   TEXT,
    paragraph_no TEXT,
    item_no      TEXT,
    prev_hash    TEXT,
    new_hash     TEXT,
    change_type  TEXT        NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_law_log_change_type
        CHECK (change_type IN ('added', 'updated', 'deleted'))
);

COMMENT ON TABLE  law_log IS '법령 변경 이력 — 새벽 배치 run_id 단위로 청크 변경 기록';
COMMENT ON COLUMN law_log.log_id       IS '로그 고유 ID';
COMMENT ON COLUMN law_log.run_id       IS '배치 실행 묶음 ID (새벽 1회 = 1 run_id)';
COMMENT ON COLUMN law_log.master_id    IS 'legal_master.id 참조 (deleted 시 null 가능)';
COMMENT ON COLUMN law_log.source_name  IS '법령명';
COMMENT ON COLUMN law_log.article_no   IS '조';
COMMENT ON COLUMN law_log.paragraph_no IS '항';
COMMENT ON COLUMN law_log.item_no      IS '호';
COMMENT ON COLUMN law_log.prev_hash    IS '변경 이전 hash (added이면 null)';
COMMENT ON COLUMN law_log.new_hash     IS '변경 이후 hash (deleted이면 null)';
COMMENT ON COLUMN law_log.change_type  IS '변경 유형 (added / updated / deleted)';
COMMENT ON COLUMN law_log.changed_at   IS '변경 감지 일시';

CREATE INDEX IF NOT EXISTS idx_law_log_run_id
    ON law_log (run_id);

CREATE INDEX IF NOT EXISTS idx_law_log_master_id
    ON law_log (master_id)
    WHERE master_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_law_log_source_name
    ON law_log (source_name);

CREATE INDEX IF NOT EXISTS idx_law_log_change_type
    ON law_log (change_type);

CREATE INDEX IF NOT EXISTS idx_law_log_changed_at
    ON law_log (changed_at);

CREATE INDEX IF NOT EXISTS idx_law_log_run_change
    ON law_log (run_id, change_type);
