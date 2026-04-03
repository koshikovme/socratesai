package com.masters.socratesai.analyzer.service;

import com.masters.socratesai.analyzer.dto.AnalyzerRequest;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.analyzer.engine.PatternDetectionEngine;
import com.masters.socratesai.analyzer.engine.ProgressEstimator;
import com.masters.socratesai.analyzer.engine.SyntaxCheckEngine;
import org.springframework.stereotype.Service;

@Service
public class AnalyzerService {

    private final SyntaxCheckEngine syntaxCheckEngine;
    private final PatternDetectionEngine patternDetectionEngine;
    private final ProgressEstimator progressEstimator;

    public AnalyzerService(
            SyntaxCheckEngine syntaxCheckEngine,
            PatternDetectionEngine patternDetectionEngine,
            ProgressEstimator progressEstimator
    ) {
        this.syntaxCheckEngine = syntaxCheckEngine;
        this.patternDetectionEngine = patternDetectionEngine;
        this.progressEstimator = progressEstimator;
    }

    public AnalyzerResult analyze(AnalyzerRequest request) {
        long start = System.currentTimeMillis();

        AnalyzerResult result = new AnalyzerResult();

        var syntax = syntaxCheckEngine.check(request.getCode(), request.getLanguage());
        if (!syntax.isCompileSuccess()) {
            result.setErrorType("SYNTAX_ERROR");
            result.setSeverity("HIGH");
            result.setCompileSuccess(false);
            result.setTestsPassed(0);
            result.setTestsFailed(0);
            result.setSuspiciousRegion(syntax.getSuspiciousRegion());
        } else {
            var pattern = patternDetectionEngine.detect(request.getCode(), request.getTaskId());

            result.setErrorType(String.valueOf(pattern.getErrorType()));
            result.setSeverity(pattern.getSeverity());
            result.setCompileSuccess(true);
            result.setTestsPassed(progressEstimator.estimateTestsPassed(request.getCode(), request.getTaskId()));
            result.setTestsFailed(progressEstimator.estimateTestsFailed(request.getCode(), request.getTaskId()));
            result.setSuspiciousRegion(pattern.getSuspiciousRegion());
            result.setSignals(pattern.getSignals());
        }

        result.setCodeLines(request.getCode() == null ? 0 : request.getCode().split("\\R").length);
        result.setAnalysisTimeMs((int) (System.currentTimeMillis() - start));
        return result;
    }
}
