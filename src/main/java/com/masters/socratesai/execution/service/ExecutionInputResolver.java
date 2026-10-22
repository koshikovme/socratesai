package com.masters.socratesai.execution.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.common.validation.ContesterLimits;
import com.masters.socratesai.execution.model.ExecutionSnapshot;
import com.masters.socratesai.task.model.ProblemTestCase;
import com.masters.socratesai.task.repo.*;
import com.masters.socratesai.task.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;

@Component @RequiredArgsConstructor
public class ExecutionInputResolver {
    private final ProblemAccess access;
    private final ProblemLanguageConfigRepository languages;
    private final AuthoringRuntimeResolver runtimes;
    private final ProblemTestCaseRepository tests;
    public Input resolve(Long problemId, Long versionId, String languageKey, String source) {
        AuthoringValidation.text(source, ContesterLimits.SOURCE_BYTES);
        if (source.isBlank() || source.length() > ContesterLimits.SOURCE_CHARACTERS) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        var version = access.published(problemId, versionId);
        var language = languages.findByProblemVersion_IdAndLanguageKey(version.getId(), languageKey)
                .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REQUEST));
        runtimes.requireConfigured(List.of(language));
        var cases = tests.findAllByProblemVersion_IdOrderByPosition(version.getId());
        if (cases.isEmpty() || cases.size() > ContesterLimits.TEST_CASES || cases.stream().noneMatch(test -> Boolean.TRUE.equals(test.getHidden()))
                || cases.stream().noneMatch(test -> !Boolean.TRUE.equals(test.getHidden()))) throw new ApiException(ApiErrorCode.CONFLICT);
        return new Input(ExecutionSnapshots.create(problemId, language, source), cases);
    }
    public record Input(ExecutionSnapshot snapshot, List<ProblemTestCase> cases) {
        @Override public String toString() { return "ExecutionInput[redacted]"; }
    }
}
