package com.masters.socratesai.task.dto;

import com.masters.socratesai.task.model.TaskDifficulty;
import com.masters.socratesai.task.model.ProblemVersionStatus;
import com.masters.socratesai.task.model.OutputComparison;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record ProblemAuthorVersionResponse(
        Long id,
        int versionNumber,
        long lockVersion,
        ProblemVersionStatus status,
        String title,
        String topic,
        TaskDifficulty difficulty,
        String statement,
        String inputFormat,
        String outputFormat,
        String constraintsText,
        OutputComparison comparisonMode,
        boolean legacySnapshot,
        OffsetDateTime publishedAt,
        List<ProblemLanguageResponse> languages,
        List<ProblemAuthorTestResponse> tests
) {}
