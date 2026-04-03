CREATE TABLE IF NOT EXISTS student_task_sessions (
     session_id UUID PRIMARY KEY,
     student_id BIGINT NOT NULL,
     task_id BIGINT NOT NULL,
     started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
     ended_at TIMESTAMPTZ,
     final_status VARCHAR(30),
     total_attempts INTEGER NOT NULL DEFAULT 0,
     total_feedback_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sessions_student
    ON student_task_sessions(student_id);

CREATE INDEX IF NOT EXISTS idx_sessions_task
    ON student_task_sessions(task_id);