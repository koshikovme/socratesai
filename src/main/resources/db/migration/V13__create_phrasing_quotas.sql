-- Durable admission counters only: no prompts, source, tokens or provider output.
CREATE TABLE phrasing_request_quotas (
    scope VARCHAR(100) NOT NULL,
    period_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 0 CHECK (request_count >= 0),
    PRIMARY KEY (scope, period_start)
);
CREATE INDEX idx_phrasing_quota_retention ON phrasing_request_quotas(period_start);
