package com.masters.socratesai.analyzer.model;

import lombok.Data;

@Data
public class SyntaxCheckResult {
    boolean compileSuccess;
    String suspiciousRegion;
}
