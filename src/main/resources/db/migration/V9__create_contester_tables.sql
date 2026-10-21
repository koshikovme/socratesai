-- Additive evolution: tasks/task_tests and every V1-V8 ML column remain intact.
ALTER TABLE tasks
    ADD COLUMN owner_id BIGINT REFERENCES users(id),
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHENTICATED'
        CHECK (visibility IN ('AUTHENTICATED', 'PUBLIC')),
    ADD COLUMN published_version_id BIGINT,
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE problem_versions (
    id BIGSERIAL PRIMARY KEY,
    problem_id BIGINT NOT NULL REFERENCES tasks(id),
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    title VARCHAR(180) NOT NULL,
    topic VARCHAR(120) NOT NULL,
    difficulty VARCHAR(20) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    statement TEXT NOT NULL,
    input_format TEXT NOT NULL DEFAULT '',
    output_format TEXT NOT NULL DEFAULT '',
    constraints_text TEXT NOT NULL DEFAULT '',
    comparison_mode VARCHAR(40) NOT NULL DEFAULT 'IGNORE_TRAILING_LINE_ENDINGS'
        CHECK (comparison_mode IN ('EXACT', 'IGNORE_TRAILING_LINE_ENDINGS')),
    legacy_snapshot BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_problem_version_number UNIQUE (problem_id, version_number),
    CONSTRAINT uq_problem_version_identity UNIQUE (id, problem_id),
    CONSTRAINT chk_version_publication CHECK ((status = 'PUBLISHED') = (published_at IS NOT NULL))
);

CREATE UNIQUE INDEX uq_problem_active_draft ON problem_versions(problem_id) WHERE status = 'DRAFT';

ALTER TABLE tasks ADD CONSTRAINT fk_task_published_version
    FOREIGN KEY (published_version_id, id) REFERENCES problem_versions(id, problem_id);

CREATE TABLE problem_language_configs (
    id BIGSERIAL PRIMARY KEY,
    problem_version_id BIGINT NOT NULL REFERENCES problem_versions(id),
    language_key VARCHAR(30) NOT NULL,
    runtime_key VARCHAR(100) NOT NULL,
    runtime_version VARCHAR(100) NOT NULL,
    runtime_verified BOOLEAN NOT NULL DEFAULT FALSE,
    starter_code TEXT,
    entry_point VARCHAR(120),
    time_limit_ms INTEGER NOT NULL CHECK (time_limit_ms > 0),
    wall_time_limit_ms INTEGER NOT NULL CHECK (wall_time_limit_ms >= time_limit_ms),
    memory_limit_kb INTEGER NOT NULL CHECK (memory_limit_kb > 0),
    output_limit_bytes INTEGER NOT NULL CHECK (output_limit_bytes BETWEEN 1 AND 65536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_problem_version_language UNIQUE (problem_version_id, language_key)
);

CREATE TABLE problem_test_cases (
    id BIGSERIAL PRIMARY KEY,
    problem_version_id BIGINT NOT NULL REFERENCES problem_versions(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    hidden BOOLEAN NOT NULL,
    legacy_test_id BIGINT UNIQUE REFERENCES task_tests(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_problem_case_position UNIQUE (problem_version_id, position),
    CONSTRAINT uq_problem_case_identity UNIQUE (id, problem_version_id)
);

CREATE TABLE collections (
    id BIGSERIAL PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(180) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHENTICATED' CHECK (visibility IN ('AUTHENTICATED', 'PUBLIC')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE collection_problems (
    id BIGSERIAL PRIMARY KEY,
    collection_id BIGINT NOT NULL REFERENCES collections(id),
    problem_id BIGINT NOT NULL REFERENCES tasks(id),
    problem_version_id BIGINT NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_collection_problem_version FOREIGN KEY (problem_version_id, problem_id)
        REFERENCES problem_versions(id, problem_id),
    CONSTRAINT uq_collection_problem UNIQUE (collection_id, problem_id),
    CONSTRAINT uq_collection_position UNIQUE (collection_id, position) DEFERRABLE INITIALLY IMMEDIATE
);

CREATE TABLE submissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id BIGINT NOT NULL REFERENCES users(id),
    problem_id BIGINT NOT NULL REFERENCES tasks(id),
    problem_version_id BIGINT NOT NULL,
    collection_id BIGINT REFERENCES collections(id),
    session_id UUID REFERENCES student_task_sessions(session_id),
    language_key VARCHAR(30) NOT NULL,
    runtime_key VARCHAR(100) NOT NULL,
    runtime_version VARCHAR(100) NOT NULL,
    source_code TEXT NOT NULL CHECK (char_length(source_code) BETWEEN 1 AND 20000),
    source_hash VARCHAR(64) NOT NULL CHECK (source_hash ~ '^[0-9a-f]{64}$'),
    time_limit_ms INTEGER NOT NULL CHECK (time_limit_ms > 0),
    wall_time_limit_ms INTEGER NOT NULL CHECK (wall_time_limit_ms >= time_limit_ms),
    memory_limit_kb INTEGER NOT NULL CHECK (memory_limit_kb > 0),
    output_limit_bytes INTEGER NOT NULL CHECK (output_limit_bytes BETWEEN 1 AND 65536),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    idempotency_key UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'DISPATCHING', 'RUNNING', 'FINISHED', 'SYSTEM_ERROR')),
    verdict VARCHAR(30) CHECK (verdict IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
        'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED')),
    tests_total INTEGER NOT NULL DEFAULT 0 CHECK (tests_total >= 0),
    tests_passed INTEGER NOT NULL DEFAULT 0 CHECK (tests_passed >= 0),
    tests_failed INTEGER NOT NULL DEFAULT 0 CHECK (tests_failed >= 0),
    time_consumed_ms INTEGER CHECK (time_consumed_ms >= 0),
    memory_consumed_bytes BIGINT CHECK (memory_consumed_bytes >= 0),
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_submission_version FOREIGN KEY (problem_version_id, problem_id)
        REFERENCES problem_versions(id, problem_id),
    CONSTRAINT fk_submission_language FOREIGN KEY (problem_version_id, language_key)
        REFERENCES problem_language_configs(problem_version_id, language_key),
    CONSTRAINT uq_submission_identity UNIQUE (id, problem_version_id),
    CONSTRAINT uq_submission_idempotency UNIQUE (student_id, idempotency_key),
    CONSTRAINT uq_submission_attempt UNIQUE (student_id, problem_id, attempt_number),
    CONSTRAINT chk_submission_counts CHECK (tests_passed + tests_failed <= tests_total),
    CONSTRAINT chk_submission_verdict CHECK ((status = 'FINISHED') = (verdict IS NOT NULL)),
    CONSTRAINT chk_submission_terminal CHECK ((status IN ('FINISHED', 'SYSTEM_ERROR')) = (finished_at IS NOT NULL)),
    CONSTRAINT chk_submission_accepted CHECK (verdict IS DISTINCT FROM 'ACCEPTED'
        OR (tests_total > 0 AND tests_passed = tests_total AND tests_failed = 0))
);

-- Sample runs and staff reference verification are not graded submissions.
CREATE TABLE execution_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requested_by BIGINT NOT NULL REFERENCES users(id),
    problem_id BIGINT NOT NULL REFERENCES tasks(id),
    problem_version_id BIGINT NOT NULL,
    language_key VARCHAR(30) NOT NULL,
    runtime_key VARCHAR(100) NOT NULL,
    runtime_version VARCHAR(100) NOT NULL,
    mode VARCHAR(30) NOT NULL CHECK (mode IN ('SAMPLE_RUN', 'REFERENCE_VERIFICATION')),
    source_code TEXT NOT NULL CHECK (char_length(source_code) BETWEEN 1 AND 20000),
    source_hash VARCHAR(64) NOT NULL CHECK (source_hash ~ '^[0-9a-f]{64}$'),
    time_limit_ms INTEGER NOT NULL CHECK (time_limit_ms > 0),
    wall_time_limit_ms INTEGER NOT NULL CHECK (wall_time_limit_ms >= time_limit_ms),
    memory_limit_kb INTEGER NOT NULL CHECK (memory_limit_kb > 0),
    output_limit_bytes INTEGER NOT NULL CHECK (output_limit_bytes BETWEEN 1 AND 65536),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_run_version FOREIGN KEY (problem_version_id, problem_id)
        REFERENCES problem_versions(id, problem_id),
    CONSTRAINT fk_run_language FOREIGN KEY (problem_version_id, language_key)
        REFERENCES problem_language_configs(problem_version_id, language_key),
    CONSTRAINT uq_run_identity UNIQUE (id, problem_version_id, mode),
    CONSTRAINT chk_run_expiration CHECK (expires_at > created_at)
);

CREATE TABLE execution_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    problem_version_id BIGINT NOT NULL REFERENCES problem_versions(id),
    submission_id UUID UNIQUE,
    run_id UUID UNIQUE,
    mode VARCHAR(30) NOT NULL CHECK (mode IN ('SUBMISSION', 'SAMPLE_RUN', 'REFERENCE_VERIFICATION')),
    status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN ('QUEUED', 'DISPATCHING', 'RUNNING', 'FINISHED', 'SYSTEM_ERROR')),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts > 0),
    generation INTEGER NOT NULL DEFAULT 0 CHECK (generation >= 0),
    lease_owner VARCHAR(120),
    lease_token UUID,
    lease_expires_at TIMESTAMPTZ,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deadline_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_job_submission FOREIGN KEY (submission_id, problem_version_id)
        REFERENCES submissions(id, problem_version_id),
    CONSTRAINT fk_job_run FOREIGN KEY (run_id, problem_version_id, mode)
        REFERENCES execution_runs(id, problem_version_id, mode),
    CONSTRAINT uq_job_identity UNIQUE (id, problem_version_id),
    CONSTRAINT chk_job_target CHECK (
        (mode = 'SUBMISSION' AND submission_id IS NOT NULL AND run_id IS NULL)
        OR (mode IN ('SAMPLE_RUN', 'REFERENCE_VERIFICATION') AND run_id IS NOT NULL AND submission_id IS NULL)),
    CONSTRAINT chk_job_attempts CHECK (attempt_count <= max_attempts),
    CONSTRAINT chk_job_lease CHECK (
        (status IN ('DISPATCHING', 'RUNNING') AND lease_owner IS NOT NULL
            AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status NOT IN ('DISPATCHING', 'RUNNING') AND lease_owner IS NULL
            AND lease_token IS NULL AND lease_expires_at IS NULL)),
    CONSTRAINT chk_job_deadline CHECK (deadline_at > created_at),
    CONSTRAINT chk_job_terminal CHECK ((status IN ('FINISHED', 'SYSTEM_ERROR')) = (finished_at IS NOT NULL))
);

-- One shared result table avoids duplicating sample/reference/submission execution.
CREATE TABLE execution_case_results (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID NOT NULL,
    problem_version_id BIGINT NOT NULL,
    test_case_id BIGINT NOT NULL,
    generation INTEGER NOT NULL DEFAULT 0 CHECK (generation >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'RUNNING', 'FINISHED', 'SYSTEM_ERROR', 'SKIPPED')),
    verdict VARCHAR(30) CHECK (verdict IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
        'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED')),
    provider_token VARCHAR(200),
    compile_output TEXT CHECK (octet_length(compile_output) <= 65536),
    stdout TEXT CHECK (octet_length(stdout) <= 65536),
    stderr TEXT CHECK (octet_length(stderr) <= 65536),
    exit_code INTEGER,
    time_consumed_ms INTEGER CHECK (time_consumed_ms >= 0),
    memory_consumed_bytes BIGINT CHECK (memory_consumed_bytes >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_case_result_job FOREIGN KEY (job_id, problem_version_id)
        REFERENCES execution_jobs(id, problem_version_id),
    CONSTRAINT fk_case_result_test FOREIGN KEY (test_case_id, problem_version_id)
        REFERENCES problem_test_cases(id, problem_version_id),
    CONSTRAINT uq_execution_case_generation UNIQUE (job_id, test_case_id, generation),
    CONSTRAINT chk_case_verdict CHECK ((status = 'FINISHED') = (verdict IS NOT NULL))
);

ALTER TABLE student_task_sessions
    ADD COLUMN problem_id BIGINT,
    ADD COLUMN problem_version_id BIGINT,
    ADD COLUMN language_key VARCHAR(30),
    ADD COLUMN context_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        CHECK (context_source IN ('LEGACY_UNKNOWN', 'NATIVE', 'IMPORTED_LEETCODE')),
    ADD CONSTRAINT fk_session_problem FOREIGN KEY (problem_version_id, problem_id)
        REFERENCES problem_versions(id, problem_id),
    ADD CONSTRAINT fk_session_language FOREIGN KEY (problem_version_id, language_key)
        REFERENCES problem_language_configs(problem_version_id, language_key),
    ADD CONSTRAINT chk_session_native_context CHECK (
        (context_source = 'LEGACY_UNKNOWN' AND problem_id IS NULL AND problem_version_id IS NULL AND language_key IS NULL)
        OR (context_source <> 'LEGACY_UNKNOWN' AND problem_id IS NOT NULL AND problem_version_id IS NOT NULL
            AND language_key IS NOT NULL AND task_id = problem_id));

ALTER TABLE interaction_logs
    ADD COLUMN problem_id BIGINT,
    ADD COLUMN problem_version_id BIGINT,
    ADD COLUMN language_key VARCHAR(30),
    ADD COLUMN context_source VARCHAR(30) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        CHECK (context_source IN ('LEGACY_UNKNOWN', 'NATIVE', 'IMPORTED_LEETCODE')),
    ADD COLUMN evidence_origin VARCHAR(30) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        CHECK (evidence_origin IN ('LEGACY_UNKNOWN', 'STATIC_HEURISTIC', 'SAMPLE_RUN', 'JUDGED_SUBMISSION')),
    ADD COLUMN code_revision BIGINT CHECK (code_revision >= 0),
    ADD COLUMN submission_id UUID REFERENCES submissions(id),
    ADD CONSTRAINT fk_interaction_problem FOREIGN KEY (problem_version_id, problem_id)
        REFERENCES problem_versions(id, problem_id),
    ADD CONSTRAINT fk_interaction_language FOREIGN KEY (problem_version_id, language_key)
        REFERENCES problem_language_configs(problem_version_id, language_key),
    ADD CONSTRAINT chk_interaction_native_context CHECK (
        (context_source = 'LEGACY_UNKNOWN' AND problem_id IS NULL AND problem_version_id IS NULL AND language_key IS NULL)
        OR (context_source <> 'LEGACY_UNKNOWN' AND problem_id IS NOT NULL AND problem_version_id IS NOT NULL
            AND language_key IS NOT NULL AND task_id = problem_id));

CREATE INDEX idx_problem_owner ON tasks(owner_id, updated_at DESC, id DESC);
CREATE INDEX idx_problem_catalog ON tasks(status, visibility, id DESC);
CREATE INDEX idx_collection_owner ON collections(owner_id, updated_at DESC, id DESC);
CREATE INDEX idx_collection_catalog ON collections(status, visibility, id DESC);
CREATE INDEX idx_collection_version ON collection_problems(problem_version_id);
CREATE INDEX idx_submission_student ON submissions(student_id, created_at DESC, id DESC);
CREATE INDEX idx_submission_problem ON submissions(problem_id, created_at DESC, id DESC);
CREATE INDEX idx_submission_version ON submissions(problem_version_id);
CREATE INDEX idx_submission_collection ON submissions(collection_id);
CREATE INDEX idx_submission_session ON submissions(session_id);
CREATE INDEX idx_run_owner ON execution_runs(requested_by, created_at DESC);
CREATE INDEX idx_run_expiration ON execution_runs(expires_at);
CREATE INDEX idx_run_version ON execution_runs(problem_version_id);
CREATE INDEX idx_job_queue ON execution_jobs(next_attempt_at, created_at, id) WHERE status = 'QUEUED';
CREATE INDEX idx_job_expired_lease ON execution_jobs(lease_expires_at) WHERE status IN ('DISPATCHING', 'RUNNING');
CREATE INDEX idx_job_version ON execution_jobs(problem_version_id);
CREATE INDEX idx_case_result_test ON execution_case_results(test_case_id);
CREATE INDEX idx_interaction_problem ON interaction_logs(problem_id, created_at);
CREATE INDEX idx_interaction_submission ON interaction_logs(submission_id);
CREATE INDEX idx_session_problem ON student_task_sessions(problem_id);
CREATE UNIQUE INDEX uq_native_active_session
    ON student_task_sessions(student_id, problem_id, language_key)
    WHERE ended_at IS NULL AND context_source <> 'LEGACY_UNKNOWN';

-- Bounded catalog snapshots only. Historical logs/sessions are deliberately NOT
-- joined to tasks by numeric equality, and old timestamps are not reinterpreted.
DO $$
DECLARE
    last_id BIGINT := -9223372036854775808;
    batch_end BIGINT;
BEGIN
    LOOP
        SELECT MAX(id) INTO batch_end FROM (
            SELECT id FROM tasks WHERE id > last_id ORDER BY id LIMIT 500
        ) batch;
        EXIT WHEN batch_end IS NULL;

        INSERT INTO problem_versions(problem_id, version_number, status, title, topic, difficulty, statement,
                                     legacy_snapshot, published_at)
        SELECT id, 1, CASE WHEN published THEN 'PUBLISHED' ELSE 'DRAFT' END,
               title, topic, difficulty, description, TRUE, CASE WHEN published THEN NOW() END
        FROM tasks WHERE id > last_id AND id <= batch_end;

        INSERT INTO problem_language_configs(problem_version_id, language_key, runtime_key, runtime_version,
            runtime_verified, starter_code, time_limit_ms, wall_time_limit_ms, memory_limit_kb, output_limit_bytes)
        SELECT v.id, COALESCE(NULLIF(LOWER(BTRIM(t.language)), ''), 'legacy-unknown'),
               'legacy-unverified', 'unverified', FALSE, t.starter_code, 2000, 5000, 262144, 65536
        FROM tasks t JOIN problem_versions v ON v.problem_id = t.id AND v.version_number = 1
        WHERE t.id > last_id AND t.id <= batch_end;

        INSERT INTO problem_test_cases(problem_version_id, position, input_data, expected_output, hidden, legacy_test_id)
        SELECT v.id, (ROW_NUMBER() OVER (PARTITION BY t.task_id ORDER BY t.id) - 1)::INTEGER,
               t.input_data, t.expected_output, t.hidden, t.id
        FROM task_tests t JOIN problem_versions v ON v.problem_id = t.task_id AND v.version_number = 1
        WHERE t.task_id > last_id AND t.task_id <= batch_end;

        UPDATE tasks t SET status = CASE WHEN t.published THEN 'PUBLISHED' ELSE 'DRAFT' END,
                           visibility = CASE WHEN t.published THEN 'PUBLIC' ELSE 'AUTHENTICATED' END,
                           published_version_id = CASE WHEN t.published THEN v.id END
        FROM problem_versions v
        WHERE v.problem_id = t.id AND v.version_number = 1 AND t.id > last_id AND t.id <= batch_end;

        last_id := batch_end;
    END LOOP;
END $$;

-- Published versions and their test/runtime definitions are immutable, even for
-- accidental direct SQL or JPA updates. Editing requires a new draft version.
CREATE FUNCTION guard_published_problem_version() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.status = 'PUBLISHED' THEN
        RAISE EXCEPTION 'Published problem versions are immutable' USING ERRCODE = '23514';
    END IF;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER protect_published_problem_version BEFORE UPDATE OR DELETE ON problem_versions
    FOR EACH ROW EXECUTE FUNCTION guard_published_problem_version();

CREATE FUNCTION guard_published_problem_child() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE
    version_id BIGINT;
    version_status VARCHAR(20);
BEGIN
    -- Lock the parent so publication cannot race with a child insert/update.
    IF TG_OP <> 'INSERT' THEN
        version_id := OLD.problem_version_id;
        SELECT status INTO version_status FROM problem_versions WHERE id = version_id FOR UPDATE;
        IF version_status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'Published problem content is immutable' USING ERRCODE = '23514';
        END IF;
    END IF;
    IF TG_OP <> 'DELETE' THEN
        version_id := NEW.problem_version_id;
        SELECT status INTO version_status FROM problem_versions WHERE id = version_id FOR UPDATE;
        IF version_status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'Published problem content is immutable' USING ERRCODE = '23514';
        END IF;
        RETURN NEW;
    END IF;
    RETURN OLD;
END $$;

CREATE TRIGGER protect_published_problem_language BEFORE INSERT OR UPDATE OR DELETE ON problem_language_configs
    FOR EACH ROW EXECUTE FUNCTION guard_published_problem_child();
CREATE TRIGGER protect_published_problem_test BEFORE INSERT OR UPDATE OR DELETE ON problem_test_cases
    FOR EACH ROW EXECUTE FUNCTION guard_published_problem_child();

CREATE FUNCTION guard_submission_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.student_id, NEW.problem_id, NEW.problem_version_id, NEW.collection_id, NEW.session_id,
           NEW.language_key, NEW.runtime_key, NEW.runtime_version, NEW.source_code, NEW.source_hash,
           NEW.time_limit_ms, NEW.wall_time_limit_ms, NEW.memory_limit_kb, NEW.output_limit_bytes,
           NEW.attempt_number, NEW.idempotency_key, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.student_id, OLD.problem_id, OLD.problem_version_id, OLD.collection_id, OLD.session_id,
           OLD.language_key, OLD.runtime_key, OLD.runtime_version, OLD.source_code, OLD.source_hash,
           OLD.time_limit_ms, OLD.wall_time_limit_ms, OLD.memory_limit_kb, OLD.output_limit_bytes,
           OLD.attempt_number, OLD.idempotency_key, OLD.created_at) THEN
        RAISE EXCEPTION 'Submission source and context are immutable' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER protect_submission_snapshot BEFORE UPDATE ON submissions
    FOR EACH ROW EXECUTE FUNCTION guard_submission_snapshot();
