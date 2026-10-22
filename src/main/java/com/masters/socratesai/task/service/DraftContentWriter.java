package com.masters.socratesai.task.service;

import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.task.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class DraftContentWriter {
    private final ProblemLanguageConfigRepository languages;
    private final ProblemTestCaseRepository tests;
    private final AuthoringRuntimeResolver runtimes;

    public void write(ProblemVersion version, ProblemDraftRequest request) {
        version.setTitle(request.title());
        version.setTopic(request.topic());
        version.setDifficulty(request.difficulty());
        version.setStatement(request.statement());
        version.setInputFormat(request.inputFormat());
        version.setOutputFormat(request.outputFormat());
        version.setConstraintsText(request.constraintsText());
        version.setComparisonMode(request.comparisonMode());
        version.setVisibility(request.visibility());
        deleteChildren(version.getId());
        languages.saveAll(request.languages().stream().map(language -> language(version, language)).toList());
        tests.saveAll(request.tests().stream().map(test -> ProblemTestCase.builder().problemVersion(version).position(test.position())
                .inputData(test.inputData()).expectedOutput(test.expectedOutput()).hidden(test.hidden()).build()).toList());
    }

    public void copy(ProblemVersion source, ProblemVersion target) {
        target.setTitle(source.getTitle()); target.setTopic(source.getTopic()); target.setDifficulty(source.getDifficulty());
        target.setStatement(source.getStatement()); target.setInputFormat(source.getInputFormat()); target.setOutputFormat(source.getOutputFormat());
        target.setConstraintsText(source.getConstraintsText()); target.setComparisonMode(source.getComparisonMode());
        target.setVisibility(source.getVisibility());
        languages.saveAll(languages.findAllByProblemVersion_IdOrderByLanguageKey(source.getId()).stream().map(language ->
                ProblemLanguageConfig.builder().problemVersion(target).languageKey(language.getLanguageKey()).runtimeKey(language.getRuntimeKey())
                        .runtimeVersion(language.getRuntimeVersion()).entryPoint(language.getEntryPoint()).starterCode(language.getStarterCode())
                        .timeLimitMs(language.getTimeLimitMs()).wallTimeLimitMs(language.getWallTimeLimitMs())
                        .memoryLimitKb(language.getMemoryLimitKb()).outputLimitBytes(language.getOutputLimitBytes()).runtimeVerified(false).build()).toList());
        tests.saveAll(tests.findAllByProblemVersion_IdOrderByPosition(source.getId()).stream().map(test -> ProblemTestCase.builder()
                .problemVersion(target).position(test.getPosition()).inputData(test.getInputData())
                .expectedOutput(test.getExpectedOutput()).hidden(test.getHidden()).build()).toList());
    }

    public void deleteChildren(Long versionId) {
        languages.deleteAllByProblemVersion_Id(versionId);
        tests.deleteAllByProblemVersion_Id(versionId);
        languages.flush(); tests.flush(); // Delete old unique keys before inserting replacements.
    }

    private ProblemLanguageConfig language(ProblemVersion version, ProblemLanguageRequest request) {
        var runtime = runtimes.configured(request.languageKey()).orElse(null);
        return ProblemLanguageConfig.builder().problemVersion(version).languageKey(request.languageKey())
                .runtimeKey(runtime == null ? "unconfigured" : runtime.runtimeKey())
                .runtimeVersion(runtime == null ? "unverified" : runtime.runtimeVersion()).runtimeVerified(false)
                .entryPoint(runtime == null ? null : runtime.entryPoint()).starterCode(request.starterCode()).timeLimitMs(request.timeLimitMs())
                .wallTimeLimitMs(request.wallTimeLimitMs()).memoryLimitKb(request.memoryLimitKb()).outputLimitBytes(request.outputLimitBytes()).build();
    }
}
