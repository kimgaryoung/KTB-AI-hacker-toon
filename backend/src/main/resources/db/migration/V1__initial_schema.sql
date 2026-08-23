CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    kakao_subject VARCHAR(100) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    profile_image_url VARCHAR(1000),
    timezone VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_user_kakao_subject UNIQUE (kakao_subject)
);

CREATE TABLE relationships (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name VARCHAR(50) NOT NULL,
    relationship_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    latest_score INTEGER,
    latest_change INTEGER,
    last_analyzed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_relationship_score CHECK (latest_score IS NULL OR latest_score BETWEEN 0 AND 100),
    CONSTRAINT ck_relationship_change CHECK (latest_change IS NULL OR latest_change BETWEEN -100 AND 100)
);
CREATE INDEX idx_relationship_user_status ON relationships(user_id, status);
CREATE INDEX idx_relationship_user_name ON relationships(user_id, name);

CREATE TABLE conversation_files (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES relationships(id) ON DELETE CASCADE,
    original_file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(500),
    size_bytes BIGINT NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    validation_status VARCHAR(20) NOT NULL,
    message_count INTEGER,
    conversation_started_at TIMESTAMP WITH TIME ZONE,
    conversation_ended_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    raw_deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_conversation_relationship ON conversation_files(relationship_id);
CREATE INDEX idx_conversation_expiry ON conversation_files(expires_at);

CREATE TABLE check_ins (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES relationships(id) ON DELETE CASCADE,
    week_start DATE NOT NULL,
    relationship_feeling INTEGER NOT NULL,
    conversation_comfort INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_checkin_relationship_week UNIQUE (relationship_id, week_start),
    CONSTRAINT ck_checkin_feeling CHECK (relationship_feeling BETWEEN 1 AND 7),
    CONSTRAINT ck_checkin_comfort CHECK (conversation_comfort BETWEEN 1 AND 7)
);
CREATE INDEX idx_checkin_user_relationship ON check_ins(user_id, relationship_id);

CREATE TABLE analysis_jobs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES relationships(id) ON DELETE CASCADE,
    conversation_file_id UUID NOT NULL REFERENCES conversation_files(id) ON DELETE CASCADE,
    check_in_id UUID NOT NULL REFERENCES check_ins(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(50) NOT NULL,
    progress INTEGER NOT NULL,
    report_id UUID,
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    failure_retryable BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_analysis_progress CHECK (progress BETWEEN 0 AND 100)
);
CREATE INDEX idx_analysis_relationship_status ON analysis_jobs(relationship_id, status);

CREATE TABLE relationship_reports (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES relationships(id) ON DELETE CASCADE,
    analysis_job_id UUID NOT NULL REFERENCES analysis_jobs(id) ON DELETE CASCADE,
    overall_score INTEGER NOT NULL,
    score_change INTEGER,
    satisfaction INTEGER NOT NULL,
    commitment INTEGER NOT NULL,
    intimacy INTEGER NOT NULL,
    trust INTEGER NOT NULL,
    passion INTEGER NOT NULL,
    love INTEGER NOT NULL,
    model_version VARCHAR(100) NOT NULL,
    scoring_policy_version VARCHAR(100) NOT NULL,
    analyzed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_report_analysis_job UNIQUE (analysis_job_id),
    CONSTRAINT ck_report_overall CHECK (overall_score BETWEEN 0 AND 100),
    CONSTRAINT ck_report_prqc CHECK (
        satisfaction BETWEEN 0 AND 100 AND commitment BETWEEN 0 AND 100 AND
        intimacy BETWEEN 0 AND 100 AND trust BETWEEN 0 AND 100 AND
        passion BETWEEN 0 AND 100 AND love BETWEEN 0 AND 100
    )
);
CREATE INDEX idx_report_relationship_analyzed ON relationship_reports(relationship_id, analyzed_at);
ALTER TABLE analysis_jobs ADD CONSTRAINT fk_analysis_report
    FOREIGN KEY (report_id) REFERENCES relationship_reports(id) ON DELETE SET NULL;

CREATE TABLE report_evidences (
    id UUID PRIMARY KEY,
    report_id UUID NOT NULL REFERENCES relationship_reports(id) ON DELETE CASCADE,
    component VARCHAR(30) NOT NULL,
    score INTEGER NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    metric_name VARCHAR(100),
    current_value DOUBLE PRECISION,
    previous_value DOUBLE PRECISION,
    metric_unit VARCHAR(30),
    metric_period VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_evidence_score CHECK (score BETWEEN 0 AND 100)
);
CREATE INDEX idx_evidence_report ON report_evidences(report_id);

CREATE TABLE consultations (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    relationship_id UUID NOT NULL REFERENCES relationships(id) ON DELETE CASCADE,
    report_id UUID NOT NULL REFERENCES relationship_reports(id) ON DELETE CASCADE,
    last_message_preview VARCHAR(160),
    last_message_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_consultation_user_updated ON consultations(user_id, updated_at);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content VARCHAR(20000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    safety_notice_type VARCHAR(50),
    safety_notice_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_message_consultation_created ON chat_messages(consultation_id, created_at);

CREATE TABLE support_resources (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    category VARCHAR(50) NOT NULL,
    region VARCHAR(10) NOT NULL,
    url VARCHAR(1000),
    phone VARCHAR(50),
    hours VARCHAR(200),
    verified_at TIMESTAMP WITH TIME ZONE NOT NULL,
    source VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INTEGER NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
);
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION(SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION(EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION(PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BYTEA NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION(PRIMARY_ID) ON DELETE CASCADE
);

INSERT INTO support_resources (
    id, name, description, category, region, url, phone, hours, verified_at, source, created_at, updated_at
) VALUES (
    '00000000-0000-0000-0000-000000000001',
    '지원 리소스 샘플',
    '운영 전 검수된 공식 상담 리소스로 교체해 주세요.',
    'MENTAL_HEALTH_COUNSELING',
    'KR',
    NULL,
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    'MVP seed - 운영 전 교체 필요',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);
