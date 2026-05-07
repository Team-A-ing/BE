-- =====================================================================
-- Migration: 2026-05-06 status CHECK 제약 + teams.leader_id 인덱스
-- 사유: Gemini PR 리뷰 — FK 자동 인덱스 X / status 무결성 강화
-- 적용: Supabase SQL Editor 에 그대로 붙여 Run.
--
-- 이미 schema.sql 로 테이블이 생성된 환경에서, 추가 변경분만 ALTER 로 적용.
-- =====================================================================

-- 1) teams.leader_id 인덱스 (FK 자동 인덱스 미생성 보완)
CREATE INDEX IF NOT EXISTS idx_teams_leader_id ON teams(leader_id);

-- 2) status / event_type CHECK 제약 (BE1 도메인만 — users.role 은 BE2 합의 후)
ALTER TABLE meetings
    DROP CONSTRAINT IF EXISTS chk_meetings_status,
    ADD  CONSTRAINT chk_meetings_status
        CHECK (status IN ('PENDING','RECORDING','ANALYZING','COMPLETED','FAILED'));

ALTER TABLE promises
    DROP CONSTRAINT IF EXISTS chk_promises_status,
    ADD  CONSTRAINT chk_promises_status
        CHECK (status IN ('PENDING','DONE','MISSED'));

ALTER TABLE career_events
    DROP CONSTRAINT IF EXISTS chk_career_events_type,
    ADD  CONSTRAINT chk_career_events_type
        CHECK (event_type IN ('ACHIEVEMENT','INITIATIVE','GROWTH','CONTRIBUTION','FEEDBACK'));
