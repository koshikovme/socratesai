package com.masters.socratesai.task.dto;

import lombok.Data;

@Data
public class CreateTaskTestRequest {
    private String inputData;
    private String expectedOutput;
    private Boolean hidden;
}
