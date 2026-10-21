package com.masters.socratesai.task.dto;

import com.masters.socratesai.task.model.TaskDifficulty;
import com.masters.socratesai.task.model.OutputComparison;
import java.util.List;
import lombok.Builder;

@Builder
public record ProblemResponse(
        Long id,
        Long problemVersionId,
        int versionNumber,
        String title,
        String topic,
        TaskDifficulty difficulty,
        String statement,
        String inputFormat,
        String outputFormat,
        String constraintsText,
        OutputComparison comparisonMode,
        List<ProblemLanguageResponse> languages,
        List<ProblemSampleResponse> samples
) {}
