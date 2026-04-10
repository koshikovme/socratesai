package com.masters.socratesai.analyzer.service;

import com.masters.socratesai.analyzer.dto.AnalyzerRequest;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.analyzer.engine.PatternDetectionEngine;
import com.masters.socratesai.analyzer.engine.ProgressEstimator;
import com.masters.socratesai.analyzer.engine.SyntaxCheckEngine;
import com.masters.socratesai.analyzer.model.ErrorType;
import com.masters.socratesai.analyzer.model.PatternDetectionResult;
import com.masters.socratesai.analyzer.model.SyntaxCheckResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyzerServiceTest {

    private final SyntaxCheckEngine syntaxCheckEngine = mock(SyntaxCheckEngine.class);
    private final PatternDetectionEngine patternDetectionEngine = mock(PatternDetectionEngine.class);
    private final ProgressEstimator progressEstimator = mock(ProgressEstimator.class);

    private final AnalyzerService service = new AnalyzerService(
            syntaxCheckEngine,
            patternDetectionEngine,
            progressEstimator
    );

    @Test
    void shouldReturnSyntaxErrorResultWhenCompilationFails() {
        AnalyzerRequest request = request("if (", "java");
        SyntaxCheckResult syntax = new SyntaxCheckResult();
        syntax.setCompileSuccess(false);
        syntax.setSuspiciousRegion("statement ending");

        when(syntaxCheckEngine.check("if (", "java")).thenReturn(syntax);

        AnalyzerResult result = service.analyze(request);

        assertThat(result.getErrorType()).isEqualTo("SYNTAX_ERROR");
        assertThat(result.getSeverity()).isEqualTo("HIGH");
        assertThat(result.isCompileSuccess()).isFalse();
        assertThat(result.getTestsPassed()).isZero();
        assertThat(result.getTestsFailed()).isZero();
        assertThat(result.getSuspiciousRegion()).isEqualTo("statement ending");
        assertThat(result.getCodeLines()).isEqualTo(1);
        assertThat(result.getAnalysisTimeMs()).isGreaterThanOrEqualTo(0);
        verify(patternDetectionEngine, never()).detect("if (", 7L);
    }

    @Test
    void shouldReturnPatternBasedResultWhenCompilationSucceeds() {
        AnalyzerRequest request = request("for (int i = 0; i <= n; i++) {\n  sum += i;\n}", "java");
        SyntaxCheckResult syntax = new SyntaxCheckResult();
        syntax.setCompileSuccess(true);

        PatternDetectionResult pattern = new PatternDetectionResult();
        pattern.setErrorType(ErrorType.OFF_BY_ONE);
        pattern.setSeverity("MEDIUM");
        pattern.setSuspiciousRegion("for loop condition");
        pattern.setSignals(Map.of("loopBoundarySuspicious", true));

        when(syntaxCheckEngine.check(request.getCode(), "java")).thenReturn(syntax);
        when(patternDetectionEngine.detect(request.getCode(), 7L)).thenReturn(pattern);
        when(progressEstimator.estimateTestsPassed(request.getCode(), 7L)).thenReturn(1);
        when(progressEstimator.estimateTestsFailed(request.getCode(), 7L)).thenReturn(2);

        AnalyzerResult result = service.analyze(request);

        assertThat(result.getErrorType()).isEqualTo("OFF_BY_ONE");
        assertThat(result.getSeverity()).isEqualTo("MEDIUM");
        assertThat(result.isCompileSuccess()).isTrue();
        assertThat(result.getTestsPassed()).isEqualTo(1);
        assertThat(result.getTestsFailed()).isEqualTo(2);
        assertThat(result.getSuspiciousRegion()).isEqualTo("for loop condition");
        assertThat(result.getSignals()).containsEntry("loopBoundarySuspicious", true);
        assertThat(result.getCodeLines()).isEqualTo(3);
        assertThat(result.getAnalysisTimeMs()).isGreaterThanOrEqualTo(0);
    }

    private AnalyzerRequest request(String code, String language) {
        AnalyzerRequest request = new AnalyzerRequest();
        request.setTaskId(7L);
        request.setLanguage(language);
        request.setCode(code);
        return request;
    }
}
