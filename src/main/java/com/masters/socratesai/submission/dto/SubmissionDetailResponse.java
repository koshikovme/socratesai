package com.masters.socratesai.submission.dto;

import lombok.Builder;

@Builder
public record SubmissionDetailResponse(
        SubmissionResponse submission,
        String sourceCode
) {
    @Override public String toString() { return "SubmissionDetailResponse[redacted]"; }
}
