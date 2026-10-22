package com.masters.socratesai.common.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApiErrorCode {
    INVALID_REQUEST(400, "Invalid request"),
    AUTHENTICATION_REQUIRED(401, "Authentication required"),
    FORBIDDEN(403, "Access denied"),
    NOT_FOUND(404, "Resource not found"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    CONFLICT(409, "Resource changed or operation conflicts with its current state"),
    VERIFICATION_REQUIRED(409, "Successful verification of the current draft is required"),
    UNSUPPORTED_MEDIA_TYPE(415, "Unsupported media type"),
    CAPACITY_EXCEEDED(429, "Execution capacity exceeded"),
    SANDBOX_UNAVAILABLE(503, "Execution sandbox unavailable"),
    MENTOR_UNAVAILABLE(503, "Mentor feedback unavailable"),
    INTERNAL_ERROR(500, "Request could not be completed");

    private final int status;
    private final String message;
}
