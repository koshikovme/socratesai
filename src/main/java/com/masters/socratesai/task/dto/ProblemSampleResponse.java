package com.masters.socratesai.task.dto;

import lombok.Builder;

@Builder
public record ProblemSampleResponse(
        int position,
        String inputData,
        String expectedOutput
) {}
