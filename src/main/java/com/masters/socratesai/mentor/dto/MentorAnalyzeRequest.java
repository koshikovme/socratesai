package com.masters.socratesai.mentor.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
    @Size(max = 20000)
    private String code;
    @NotNull
    @Min(1)
    private Integer attemptNo;
}
