-- Additive only: interaction_logs and all prior ML contracts are unchanged.
CREATE TABLE administration_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    bootstrap_completed BOOLEAN NOT NULL
);
INSERT INTO administration_state(id, bootstrap_completed)
VALUES (1, EXISTS (SELECT 1 FROM users WHERE role = 'ADMIN'));

CREATE TABLE role_change_audit (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT REFERENCES users(id),
    target_id BIGINT NOT NULL REFERENCES users(id),
    previous_role VARCHAR(20) CHECK (previous_role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    new_role VARCHAR(20) NOT NULL CHECK (new_role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    reason VARCHAR(500) NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('BOOTSTRAP', 'ROLE_CHANGE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_role_audit_actor CHECK ((action = 'BOOTSTRAP' AND actor_id IS NULL)
        OR (action = 'ROLE_CHANGE' AND actor_id IS NOT NULL))
);
CREATE INDEX idx_role_audit_target ON role_change_audit(target_id, created_at DESC);

CREATE TABLE problem_reference_verifications (
    id UUID PRIMARY KEY,
    problem_version_id BIGINT NOT NULL REFERENCES problem_versions(id),
    requested_by BIGINT NOT NULL REFERENCES users(id),
    draft_hash VARCHAR(64) NOT NULL CHECK (draft_hash ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'FINISHED', 'SYSTEM_ERROR')),
    verdict VARCHAR(30) CHECK (verdict IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
        'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'OUTPUT_LIMIT_EXCEEDED')),
    tests_total INTEGER NOT NULL CHECK (tests_total BETWEEN 1 AND 1000),
    tests_passed INTEGER NOT NULL DEFAULT 0 CHECK (tests_passed >= 0 AND tests_passed <= tests_total),
    failure_code VARCHAR(80),
    deadline_at TIMESTAMPTZ NOT NULL,
    valid_until TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_reference_verification_identity UNIQUE (id, problem_version_id),
    CONSTRAINT chk_verification_verdict CHECK ((status = 'FINISHED') = (verdict IS NOT NULL)),
    CONSTRAINT chk_verification_terminal CHECK ((status IN ('FINISHED', 'SYSTEM_ERROR')) = (finished_at IS NOT NULL)),
    CONSTRAINT chk_verification_accepted CHECK (verdict IS DISTINCT FROM 'ACCEPTED' OR tests_passed = tests_total),
    CONSTRAINT chk_verification_deadline CHECK (deadline_at > created_at AND valid_until >= deadline_at)
);
CREATE UNIQUE INDEX uq_active_reference_verification ON problem_reference_verifications(problem_version_id)
    WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX idx_reference_verification_version ON problem_reference_verifications(problem_version_id, created_at DESC);

ALTER TABLE problem_versions ADD COLUMN reference_verification_id UUID;
ALTER TABLE problem_versions ADD CONSTRAINT fk_version_reference_verification
    FOREIGN KEY (reference_verification_id, id) REFERENCES problem_reference_verifications(id, problem_version_id);
ALTER TABLE problem_versions ADD COLUMN visibility VARCHAR(20) NOT NULL DEFAULT 'AUTHENTICATED'
    CHECK (visibility IN ('AUTHENTICATED', 'PUBLIC'));
-- Published rows are immutable under V9; initialize only drafts. Legacy public
-- visibility is intentionally not sufficient for the new verified student API.
UPDATE problem_versions v SET visibility = t.visibility FROM tasks t
    WHERE t.id = v.problem_id AND v.status = 'DRAFT';

-- Historical audits are append-only, including accidental ORM/direct SQL writes.
CREATE FUNCTION reject_role_audit_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Audit records are immutable' USING ERRCODE = '23514';
END $$;
CREATE TRIGGER protect_role_change_audit BEFORE UPDATE OR DELETE ON role_change_audit
    FOR EACH ROW EXECUTE FUNCTION reject_role_audit_mutation();
