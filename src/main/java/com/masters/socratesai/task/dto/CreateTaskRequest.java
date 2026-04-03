package com.masters.socratesai.task.dto;

import com.masters.socratesai.task.model.TaskDifficulty;
import lombok.Data;
import java.util.List;

@Data
public class CreateTaskRequest {
    private String title;
    private String topic;
    private TaskDifficulty difficulty;
    private String language;
    private String description;
    private String starterCode;
    private Boolean published;
    private List<CreateTaskTestRequest> tests;
}