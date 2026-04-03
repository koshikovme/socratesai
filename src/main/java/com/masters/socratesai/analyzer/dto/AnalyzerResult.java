package com.masters.socratesai.analyzer.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AnalyzerResult {
    private String errorType;
    private String severity;
    private boolean compileSuccess;
    private int testsPassed;
    private int testsFailed;
    private String suspiciousRegion;
    private int analysisTimeMs;
    private int codeLines;
    private Map<String, Object> signals;
}