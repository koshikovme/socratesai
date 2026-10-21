package com.masters.socratesai.submission.dto;

import lombok.Builder;

@Builder
public record SubmissionDetailResponse(
        SubmissionResponse submission,
        String sourceCode
) {}
