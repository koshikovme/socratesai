package com.masters.socratesai.task.dto;

import com.masters.socratesai.task.model.TaskDifficulty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskResponse {
    private Long id;
    private String title;
    private String topic;
    private TaskDifficulty difficulty;
    private String language;
    private String description;
    private String starterCode;
    private Boolean published;
    private List<TaskTestResponse> tests;

    @Data
    @Builder
    public static class TaskTestResponse {
        private Long id;
        private String inputData;
        private String expectedOutput;
        private Boolean hidden;
    }
}
