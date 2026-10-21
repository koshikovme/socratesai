package com.masters.socratesai.task.dto;

import lombok.Builder;

@Builder
public record ProblemAuthorTestResponse(
        Long id,
        int position,
        String inputData,
        String expectedOutput,
        boolean hidden
) {}
