package com.masters.socratesai.mentor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MentorAnalyzeRequest {
    @NotNull
    private Long studentId;
    @NotNull
    private Long taskId;
    @NotBlank
    private String language;
    @NotBlank
    private String code;
    @NotNull
    @Min(1)
    private Integer attemptNo;
}
