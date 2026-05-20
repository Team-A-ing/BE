ALTER TABLE meetings
    ADD COLUMN IF NOT EXISTS title VARCHAR(100);

UPDATE meetings
SET title = '1:1 Meeting'
WHERE title IS NULL;

ALTER TABLE meetings
    ALTER COLUMN title SET NOT NULL;

UPDATE meetings
SET status = CASE status
    WHEN 'PENDING' THEN 'CREATED'
    WHEN 'RECORDING' THEN 'TRANSCRIBING'
    ELSE status
END;

ALTER TABLE meetings
    DROP CONSTRAINT IF EXISTS chk_meetings_status;

ALTER TABLE meetings
    ALTER COLUMN status SET DEFAULT 'CREATED';

ALTER TABLE meetings
    ADD CONSTRAINT chk_meetings_status
    CHECK (status IN ('CREATED', 'TRANSCRIBING', 'ANALYZING', 'COMPLETED', 'FAILED'));
