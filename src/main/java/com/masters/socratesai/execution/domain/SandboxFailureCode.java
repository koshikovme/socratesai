package com.masters.socratesai.execution.domain;

/** Stable safe error codes; provider bodies and exception causes must not escape. */
public enum SandboxFailureCode {
    DISABLED, BUSY, INVALID_REQUEST, RUNTIME_UNAVAILABLE, DEADLINE_EXCEEDED,
    CANCELLED, TRANSPORT_ERROR, PROVIDER_REJECTED, INVALID_RESPONSE,
    RESPONSE_TOO_LARGE, SUBMISSION_UNKNOWN, PROVIDER_INTERNAL_ERROR, POLL_LIMIT_EXCEEDED
}
