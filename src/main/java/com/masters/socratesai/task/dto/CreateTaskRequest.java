package com.masters.socratesai.task.dto;

import com.masters.socratesai.task.model.TaskDifficulty;
import lombok.Data;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import static com.masters.socratesai.common.validation.ContesterLimits.*;

@Data
public class CreateTaskRequest {
    @NotBlank @Size(max = 180) private String title;
    @NotBlank @Size(max = 120) private String topic;
    @NotNull private TaskDifficulty difficulty;
    @NotBlank @Pattern(regexp = LANGUAGE_KEY) private String language;
    @NotBlank @Size(max = STATEMENT_CHARACTERS) private String description;
    @Size(max = SOURCE_CHARACTERS) private String starterCode;
    private Boolean published;
    @Size(max = TEST_CASES) private List<@NotNull @Valid CreateTaskTestRequest> tests;
}
