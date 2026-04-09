package com.masters.socratesai.mentor.dto;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MentorRequest {
    @NotNull
    private Long studentId;
    private UUID sessionId;
    @NotNull
    private Long taskId;
    @NotNull
    @Min(1)
    private Integer attemptNo;
    @NotBlank
    private String code;
    @Valid
    @NotNull
    private AnalyzerResult analyzerResult;
}
