package com.masters.socratesai.analyzer.model;

import lombok.Data;

@Data
public class SyntaxCheckResult {
    /** Legacy heuristic gate; no compiler is invoked by the edit analyzer. */
    boolean compileSuccess;
    String suspiciousRegion;
}
