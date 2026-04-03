package com.masters.socratesai.analyzer.model;

import lombok.Data;

import java.util.Map;

@Data
public class PatternDetectionResult {
    ErrorType errorType;
    String severity;
    String suspiciousRegion;
    Map<String, Object> signals;
}
