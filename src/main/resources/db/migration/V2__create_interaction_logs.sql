CREATE TABLE IF NOT EXISTS interaction_logs (
    interaction_id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    student_id BIGINT NOT NULL,
    task_id BIGINT NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,

    error_type VARCHAR(50),
    same_error_count INTEGER NOT NULL DEFAULT 0,
    total_errors_seen INTEGER NOT NULL DEFAULT 0,

    compile_success BOOLEAN,
    tests_passed INTEGER,
    tests_failed INTEGER,

    last_feedback_action VARCHAR(50),
    feedback_action VARCHAR(50) NOT NULL,
    feedback_text TEXT,

    feedback_source VARCHAR(30) NOT NULL DEFAULT 'template',
    policy_version VARCHAR(50),
    feedback_version VARCHAR(50),

    analysis_time_ms INTEGER NOT NULL DEFAULT 0,
    policy_time_ms INTEGER NOT NULL DEFAULT 0,
    feedback_time_ms INTEGER NOT NULL DEFAULT 0,
    total_latency_ms INTEGER NOT NULL DEFAULT 0,

    fixed_after_ms INTEGER,
    resolved_after_feedback BOOLEAN,
    direct_solution_violation BOOLEAN NOT NULL DEFAULT FALSE,

    code_hash VARCHAR(128),
    code_lines INTEGER,
    suspicious_region VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_interaction_session
        FOREIGN KEY (session_id) REFERENCES student_task_sessions(session_id)
            ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_interaction_session
    ON interaction_logs(session_id);

CREATE INDEX IF NOT EXISTS idx_interaction_student_task
    ON interaction_logs(student_id, task_id);

CREATE INDEX IF NOT EXISTS idx_interaction_session_time
    ON interaction_logs(session_id, created_at);