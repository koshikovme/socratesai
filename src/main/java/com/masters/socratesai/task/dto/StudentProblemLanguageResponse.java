package com.masters.socratesai.task.dto;

public record StudentProblemLanguageResponse(String languageKey, String starterCode, String entryPoint,
        int timeLimitMs, int wallTimeLimitMs, int memoryLimitKb, int outputLimitBytes) {}
