package com.masters.socratesai.task.dto;

import lombok.Builder;

@Builder
public record ProblemLanguageResponse(
        String languageKey,
        String runtimeKey,
        String runtimeVersion,
        boolean runtimeVerified,
        String starterCode,
        String entryPoint,
        int timeLimitMs,
        int wallTimeLimitMs,
        int memoryLimitKb,
        int outputLimitBytes
) {}
