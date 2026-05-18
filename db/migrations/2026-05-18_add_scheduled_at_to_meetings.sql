-- meetings 테이블에 scheduled_at 컬럼 추가
-- BE1: 미팅 단건 조회 API (GET /api/v1/meetings/{meetingId}) 대응
ALTER TABLE meetings ADD COLUMN IF NOT EXISTS scheduled_at TIMESTAMP NULL;
