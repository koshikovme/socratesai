ALTER TABLE interaction_logs
    ADD COLUMN IF NOT EXISTS severity VARCHAR(20),
    ADD COLUMN IF NOT EXISTS last_feedback_success BOOLEAN,
    ADD COLUMN IF NOT EXISTS total_feedback_count_in_session INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS has_suspicious_region BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE interaction_logs
SET has_suspicious_region = COALESCE(NULLIF(BTRIM(suspicious_region), ''), '') <> ''
WHERE has_suspicious_region IS DISTINCT FROM (COALESCE(NULLIF(BTRIM(suspicious_region), ''), '') <> '');

UPDATE interaction_logs
SET total_feedback_count_in_session = COALESCE(total_errors_seen, 0)
WHERE total_feedback_count_in_session = 0;
