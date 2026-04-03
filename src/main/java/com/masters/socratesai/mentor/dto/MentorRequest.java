package com.masters.socratesai.mentor.dto;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import lombok.Data;

import java.util.UUID;

@Data
public class MentorRequest {
    private Long studentId;
    private UUID sessionId;
    private Long taskId;
    private Integer attemptNo;
    private String code;
    private AnalyzerResult analyzerResult;
}