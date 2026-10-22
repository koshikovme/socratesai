package com.masters.socratesai.mentor.dto;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class MentorRequest {
    private String languageKey;
    @NotNull
    private Long studentId;
    private UUID sessionId;
    @NotNull
    private Long taskId;
    @NotNull
    @Min(1)
    private Integer attemptNo;
    @NotBlank
    @Size(max = 20000)
    @lombok.ToString.Exclude
    private String code;
    @Valid
    @NotNull
    @lombok.ToString.Exclude
    private AnalyzerResult analyzerResult;
}
