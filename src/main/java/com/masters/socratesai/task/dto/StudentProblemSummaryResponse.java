package com.masters.socratesai.task.dto;

import com.masters.socratesai.task.model.TaskDifficulty;

public record StudentProblemSummaryResponse(Long id, Long problemVersionId, String title, String topic, TaskDifficulty difficulty) {}
