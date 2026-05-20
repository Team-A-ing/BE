-- teams 테이블에 invite_code 컬럼 추가
ALTER TABLE teams
    ADD COLUMN IF NOT EXISTS invite_code VARCHAR(20) UNIQUE;

-- 기존 팀들에 invite_code 생성 (READB-XXXXXX 형식)
UPDATE teams
SET invite_code = 'READB-' || upper(substring(md5(random()::text || id::text), 1, 6))
WHERE invite_code IS NULL;

-- 이후 신규 팀은 애플리케이션에서 생성
