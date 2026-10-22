package com.masters.socratesai.task.dto;

import lombok.Data;
import jakarta.validation.constraints.*;
import static com.masters.socratesai.common.validation.ContesterLimits.*;

@Data
public class CreateTaskTestRequest {
    @NotNull @Size(max = TEST_DATA_CHARACTERS) private String inputData;
    @NotNull @Size(max = TEST_DATA_CHARACTERS) private String expectedOutput;
    private Boolean hidden;
}
