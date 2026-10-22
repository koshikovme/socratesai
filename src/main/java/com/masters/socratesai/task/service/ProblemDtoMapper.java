package com.masters.socratesai.task.service;

import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.task.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class ProblemDtoMapper {
    private final ProblemLanguageConfigRepository languages;
    private final ProblemTestCaseRepository tests;

    public ProblemAuthorResponse author(Task problem, ProblemVersion version) {
        return new ProblemAuthorResponse(problem.getId(), problem.getOwnerId(), problem.getStatus(), version == null ? problem.getVisibility() : version.getVisibility(),
                problem.getPublishedVersionId(), problem.getLockVersion(), version == null ? null : authorVersion(version));
    }

    private ProblemAuthorVersionResponse authorVersion(ProblemVersion v) {
        return new ProblemAuthorVersionResponse(v.getId(), v.getVersionNumber(), v.getLockVersion(), v.getStatus(),
                v.getTitle(), v.getTopic(), v.getDifficulty(), v.getStatement(), v.getInputFormat(), v.getOutputFormat(),
                v.getConstraintsText(), v.getComparisonMode(), v.getLegacySnapshot(), v.getPublishedAt(),
                languages.findAllByProblemVersion_IdOrderByLanguageKey(v.getId()).stream().map(this::authorLanguage).toList(),
                tests.findAllByProblemVersion_IdOrderByPosition(v.getId()).stream().map(t -> new ProblemAuthorTestResponse(
                        t.getId(), t.getPosition(), t.getInputData(), t.getExpectedOutput(), t.getHidden())).toList());
    }

    public ProblemResponse student(ProblemVersion v) {
        var publicCases = tests.findAllByProblemVersion_IdAndHiddenFalseOrderByPosition(v.getId());
        return new ProblemResponse(v.getProblem().getId(), v.getId(), v.getVersionNumber(), v.getTitle(), v.getTopic(),
                v.getDifficulty(), v.getStatement(), v.getInputFormat(), v.getOutputFormat(), v.getConstraintsText(), v.getComparisonMode(),
                languages.findAllByProblemVersion_IdOrderByLanguageKey(v.getId()).stream().map(l -> new StudentProblemLanguageResponse(
                        l.getLanguageKey(), l.getStarterCode(), l.getEntryPoint(), l.getTimeLimitMs(), l.getWallTimeLimitMs(),
                        l.getMemoryLimitKb(), l.getOutputLimitBytes())).toList(),
                java.util.stream.IntStream.range(0, publicCases.size()).mapToObj(index -> new ProblemSampleResponse(
                        index, publicCases.get(index).getInputData(), publicCases.get(index).getExpectedOutput())).toList());
    }

    private ProblemLanguageResponse authorLanguage(ProblemLanguageConfig l) {
        return new ProblemLanguageResponse(l.getLanguageKey(), l.getRuntimeKey(), l.getRuntimeVersion(), l.getRuntimeVerified(),
                l.getStarterCode(), l.getEntryPoint(), l.getTimeLimitMs(), l.getWallTimeLimitMs(), l.getMemoryLimitKb(), l.getOutputLimitBytes());
    }
}
