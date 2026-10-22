-- Execution checkpoints survive lease changes. A SENDING checkpoint without a
-- token is ambiguous and must never automatically POST the same program again.
ALTER TABLE execution_case_results ADD COLUMN dispatch_state VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (dispatch_state IN ('PENDING', 'SENDING', 'TICKETED', 'COMPLETE'));
UPDATE execution_case_results SET dispatch_state = CASE
    WHEN status = 'FINISHED' THEN 'COMPLETE'
    WHEN provider_token IS NOT NULL THEN 'TICKETED' ELSE 'PENDING' END;
ALTER TABLE execution_jobs ADD COLUMN retry_allowed BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE execution_case_results ADD COLUMN provider_deadline_at TIMESTAMPTZ;
ALTER TABLE execution_runs ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX uq_run_idempotency ON execution_runs(requested_by, idempotency_key);
ALTER TABLE execution_case_results ADD CONSTRAINT chk_ticket_checkpoint
    CHECK (dispatch_state <> 'TICKETED' OR provider_token IS NOT NULL);
CREATE INDEX idx_job_safe_retry ON execution_jobs(next_attempt_at, id)
    WHERE status = 'SYSTEM_ERROR' AND retry_allowed;

CREATE TABLE student_problem_progress (
    id BIGSERIAL PRIMARY KEY,
    student_id BIGINT NOT NULL REFERENCES users(id),
    problem_id BIGINT NOT NULL REFERENCES tasks(id),
    formal_attempts INTEGER NOT NULL DEFAULT 0 CHECK (formal_attempts >= 0),
    accepted_submission_id UUID REFERENCES submissions(id),
    accepted_at TIMESTAMPTZ,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    best_tests_passed INTEGER NOT NULL DEFAULT 0 CHECK (best_tests_passed >= 0),
    last_tests_failed INTEGER NOT NULL DEFAULT 0 CHECK (last_tests_failed >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    UNIQUE (student_id, problem_id),
    CHECK ((accepted_submission_id IS NULL) = (accepted_at IS NULL))
);
-- Only actual graded submissions are eligible for backfill. Never infer native
-- problem identity or acceptance from interaction_logs / ML estimates.
INSERT INTO student_problem_progress(student_id, problem_id, formal_attempts, last_activity_at, best_tests_passed)
SELECT student_id, problem_id, MAX(attempt_number), MAX(created_at), MAX(tests_passed)
FROM submissions GROUP BY student_id, problem_id;
UPDATE student_problem_progress p SET last_tests_failed = s.tests_failed
FROM (SELECT DISTINCT ON (student_id, problem_id) student_id, problem_id, tests_failed
      FROM submissions ORDER BY student_id, problem_id, attempt_number DESC) s
WHERE p.student_id = s.student_id AND p.problem_id = s.problem_id;
UPDATE student_problem_progress p SET accepted_submission_id = a.id, accepted_at = a.finished_at
FROM (SELECT DISTINCT ON (student_id, problem_id) id, student_id, problem_id, finished_at
      FROM submissions WHERE status = 'FINISHED' AND verdict = 'ACCEPTED'
      ORDER BY student_id, problem_id, finished_at, id) a
WHERE p.student_id = a.student_id AND p.problem_id = a.problem_id;

CREATE FUNCTION validate_real_progress() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.accepted_submission_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM submissions s WHERE s.id = NEW.accepted_submission_id
        AND s.student_id = NEW.student_id AND s.problem_id = NEW.problem_id
        AND s.status = 'FINISHED' AND s.verdict = 'ACCEPTED'
        AND s.tests_total > 0 AND s.tests_passed = s.tests_total AND s.tests_failed = 0
    ) THEN RAISE EXCEPTION 'Invalid progress evidence' USING ERRCODE = '23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER validate_real_progress BEFORE INSERT OR UPDATE ON student_problem_progress
    FOR EACH ROW EXECUTE FUNCTION validate_real_progress();
