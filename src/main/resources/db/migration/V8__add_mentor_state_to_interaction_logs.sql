ALTER TABLE interaction_logs
    ADD COLUMN IF NOT EXISTS mentor_state VARCHAR(50),
    ADD COLUMN IF NOT EXISTS mentor_state_confidence DOUBLE PRECISION;
