package com.masters.socratesai.analyzer.dto;

import lombok.Data;

@Data
public class AnalyzerRequest {
    private Long studentId;
    private Long taskId;
    private String language;
    private String code;
    private Integer attemptNo;
}