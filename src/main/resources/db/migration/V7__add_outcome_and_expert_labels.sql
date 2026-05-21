ALTER TABLE interaction_logs
    ADD COLUMN IF NOT EXISTS feedback_helpful BOOLEAN,
    ADD COLUMN IF NOT EXISTS feedback_rating INTEGER,
    ADD COLUMN IF NOT EXISTS student_comment TEXT,
    ADD COLUMN IF NOT EXISTS repeated_same_error_after_feedback BOOLEAN;

CREATE TABLE IF NOT EXISTS expert_action_labels (
    label_id UUID PRIMARY KEY,
    interaction_id UUID NOT NULL REFERENCES interaction_logs(interaction_id) ON DELETE CASCADE,
    reviewer_id BIGINT NOT NULL,
    target_feedback_action VARCHAR(50) NOT NULL,
    confidence INTEGER,
    rationale TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_expert_label_interaction_reviewer UNIQUE (interaction_id, reviewer_id),
    CONSTRAINT chk_expert_label_confidence CHECK (confidence IS NULL OR (confidence BETWEEN 1 AND 5))
);

CREATE INDEX IF NOT EXISTS idx_expert_action_labels_interaction_id
    ON expert_action_labels(interaction_id);

CREATE INDEX IF NOT EXISTS idx_expert_action_labels_reviewer_id
    ON expert_action_labels(reviewer_id);
