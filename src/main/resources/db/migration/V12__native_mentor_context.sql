-- Legacy feature columns and identifiers remain unchanged.
ALTER TABLE student_task_sessions ADD COLUMN editor_session_id UUID;
ALTER TABLE student_task_sessions ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
DROP INDEX uq_native_active_session;
CREATE UNIQUE INDEX uq_native_editor_session ON student_task_sessions
    (student_id, problem_id, problem_version_id, language_key, editor_session_id)
    WHERE ended_at IS NULL AND context_source <> 'LEGACY_UNKNOWN' AND editor_session_id IS NOT NULL;
CREATE UNIQUE INDEX uq_native_legacy_session ON student_task_sessions(student_id, problem_id, language_key)
    WHERE ended_at IS NULL AND context_source <> 'LEGACY_UNKNOWN' AND editor_session_id IS NULL;
ALTER TABLE interaction_logs ADD COLUMN editor_session_id UUID;
ALTER TABLE interaction_logs ADD COLUMN execution_job_id UUID REFERENCES execution_jobs(id) ON DELETE SET NULL;
ALTER TABLE interaction_logs ADD COLUMN execution_known BOOLEAN;
ALTER TABLE interaction_logs ADD COLUMN policy_fallback_reason VARCHAR(80);
CREATE INDEX idx_submission_mentor_evidence ON submissions
    (student_id, problem_version_id, language_key, source_hash) WHERE status = 'FINISHED';
CREATE INDEX idx_run_mentor_evidence ON execution_runs(requested_by, problem_version_id, language_key, source_hash);
