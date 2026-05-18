-- =====================================================================
-- ReadB DB Schema (Supabase / PostgreSQL)
-- 작성: BE1 (5/4)
-- 기준: 준영 ERD + 5/4 변경사항(Speech Act 기반, AI 추론 점수 제거)
--
-- 실행 방법
--   Supabase Dashboard → SQL Editor 에 그대로 붙여넣고 Run.
--   pgvector 는 RAG 단계에서 활성화 예정 (이번 주 데모에는 불필요).
-- =====================================================================

-- ---------------------------------------------------------------------
-- USERS  (BE2 도메인 — 마이그레이션 시 BE2 합의 후 변경)
-- 변경: churned_at 컬럼 추가 (퇴사 예측 모델 학습용)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL,           -- LEADER | MEMBER
    job_title       VARCHAR(100),
    team_id         BIGINT,
    churned_at      TIMESTAMP NULL,                  -- 퇴사 시점 (예측 모델용)
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_users_team_id ON users(team_id);
CREATE INDEX IF NOT EXISTS idx_users_role    ON users(role);

-- ---------------------------------------------------------------------
-- TEAMS  (BE2 도메인)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS teams (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    leader_id   BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_teams_leader FOREIGN KEY (leader_id) REFERENCES users(id)
);
CREATE INDEX IF NOT EXISTS idx_teams_leader_id ON teams(leader_id);

-- users.team_id FK는 teams 생성 후 추가 (순환 참조)
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS fk_users_team;
ALTER TABLE users
    ADD CONSTRAINT fk_users_team FOREIGN KEY (team_id) REFERENCES teams(id);

-- ---------------------------------------------------------------------
-- MEETINGS  (BE1 도메인)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS meetings (
    id          BIGSERIAL PRIMARY KEY,
    team_id     BIGINT NOT NULL,
    title       VARCHAR(100) NOT NULL DEFAULT '1:1 Meeting',
    leader_id   BIGINT NOT NULL,
    member_id   BIGINT NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'CREATED',  -- CREATED|TRANSCRIBING|ANALYZING|COMPLETED|FAILED
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_meetings_team   FOREIGN KEY (team_id)   REFERENCES teams(id),
    CONSTRAINT fk_meetings_leader FOREIGN KEY (leader_id) REFERENCES users(id),
    CONSTRAINT fk_meetings_member FOREIGN KEY (member_id) REFERENCES users(id),
    CONSTRAINT chk_meetings_status CHECK (status IN ('CREATED','TRANSCRIBING','ANALYZING','COMPLETED','FAILED'))
);
CREATE INDEX IF NOT EXISTS idx_meetings_team_id   ON meetings(team_id);
CREATE INDEX IF NOT EXISTS idx_meetings_leader_id ON meetings(leader_id);
CREATE INDEX IF NOT EXISTS idx_meetings_member_id ON meetings(member_id);
CREATE INDEX IF NOT EXISTS idx_meetings_status    ON meetings(status);

-- ---------------------------------------------------------------------
-- RECORDINGS  (BE1 도메인)
-- meeting:recording = 1:1
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS recordings (
    id            BIGSERIAL PRIMARY KEY,
    meeting_id    BIGINT NOT NULL UNIQUE,
    file_url      VARCHAR(500),               -- 분석 완료 후 NULL 처리 (영구 삭제 정책)
    duration_sec  INTEGER,
    transcript    TEXT,
    created_at    TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_recordings_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id)
);

-- ---------------------------------------------------------------------
-- SURVEYS  (BE2 도메인)
-- 변경: scores JSONB 안에 issues / energy_level / desired_roles / survey_score 보관
-- (member_id, meeting_id) UNIQUE
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS surveys (
    id            BIGSERIAL PRIMARY KEY,
    meeting_id    BIGINT NOT NULL,
    member_id     BIGINT NOT NULL,
    scores        JSONB,
    submitted_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_surveys_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id),
    CONSTRAINT fk_surveys_member  FOREIGN KEY (member_id)  REFERENCES users(id),
    CONSTRAINT uq_surveys_meeting_member UNIQUE (meeting_id, member_id)
);

-- ---------------------------------------------------------------------
-- ANALYSES  (BE1 도메인)
-- 변경:
--   제거 — gap_score, surface_score, inferred_score (AI 추론 점수 폐기)
--   추가 — alignment_gap, honesty_gap, execution_gap, safety_score
--   추가 — speech_acts JSONB (Vulnerability / Dissent / Initiative 원문+타임스탬프)
--   추가 — baseline_data JSONB (최근 3회 평균)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analyses (
    id                BIGSERIAL PRIMARY KEY,
    meeting_id        BIGINT NOT NULL UNIQUE,
    alignment_gap     DOUBLE PRECISION,
    honesty_gap       DOUBLE PRECISION,
    execution_gap     DOUBLE PRECISION,
    safety_score      DOUBLE PRECISION,
    speech_acts       JSONB,    -- {"vulnerability":[{text,timestamp}], "dissent":[...], "initiative":[...]}
    blocker_keywords  JSONB,
    leader_feedback   JSONB,
    member_feedback   JSONB,
    career_tags       JSONB,
    baseline_data     JSONB,    -- {prev_avg_vulnerability, prev_avg_dissent, prev_avg_initiative}
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_analyses_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id)
);

-- ---------------------------------------------------------------------
-- PROMISES  (BE1 도메인)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS promises (
    id          BIGSERIAL PRIMARY KEY,
    meeting_id  BIGINT NOT NULL,
    owner_id    BIGINT NOT NULL,
    content     TEXT NOT NULL,
    deadline    DATE,
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|DONE|MISSED
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_promises_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id),
    CONSTRAINT fk_promises_owner   FOREIGN KEY (owner_id)   REFERENCES users(id),
    CONSTRAINT chk_promises_status CHECK (status IN ('PENDING','DONE','MISSED'))
);
CREATE INDEX IF NOT EXISTS idx_promises_owner_id ON promises(owner_id);
CREATE INDEX IF NOT EXISTS idx_promises_status   ON promises(status);

-- ---------------------------------------------------------------------
-- CAREER_EVENTS  (BE1 도메인 / 신규)
-- 멤버 Career Memory 타임라인용. Fact-Based 원칙에 따라
-- evidence(JSONB)에 원문 인용 + 타임스탬프 저장.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS career_events (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    meeting_id   BIGINT,                                       -- 미팅 외 입력 가능 → nullable
    event_type   VARCHAR(20) NOT NULL,                         -- ACHIEVEMENT|INITIATIVE|GROWTH|CONTRIBUTION|FEEDBACK
    title        VARCHAR(200) NOT NULL,
    description  TEXT,
    evidence     JSONB,                                        -- {quote, timestamp, source_meeting_id}
    occurred_at  TIMESTAMP,
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_career_events_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_career_events_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id),
    CONSTRAINT chk_career_events_type   CHECK (event_type IN ('ACHIEVEMENT','INITIATIVE','GROWTH','CONTRIBUTION','FEEDBACK'))
);
CREATE INDEX IF NOT EXISTS idx_career_events_user_id    ON career_events(user_id);
CREATE INDEX IF NOT EXISTS idx_career_events_meeting_id ON career_events(meeting_id);
CREATE INDEX IF NOT EXISTS idx_career_events_type       ON career_events(event_type);
