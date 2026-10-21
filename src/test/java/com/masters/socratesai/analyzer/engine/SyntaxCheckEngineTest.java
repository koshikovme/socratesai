package com.masters.socratesai.analyzer.engine;

import com.masters.socratesai.analyzer.model.SyntaxCheckResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyntaxCheckEngineTest {

    private final SyntaxCheckEngine engine = new SyntaxCheckEngine();

    @Test
    void shouldRejectBlankCode() {
        SyntaxCheckResult result = engine.check("   ", "java");

        assertThat(result.isCompileSuccess()).isFalse();
        assertThat(result.getSuspiciousRegion()).isEqualTo("empty editor");
    }

    @Test
    void shouldRejectJavaStatementWithoutSemicolon() {
        SyntaxCheckResult result = engine.check("int x = 1", "java");

        assertThat(result.isCompileSuccess()).isFalse();
        assertThat(result.getSuspiciousRegion()).isEqualTo("statement ending");
    }

    @Test
    void shouldFindNoObviousIssueInTerminatedJavaDeclaration() {
        SyntaxCheckResult result = engine.check("int x = 1;", "java");

        assertThat(result.isCompileSuccess()).isTrue();
        assertThat(result.getSuspiciousRegion()).isNull();
    }

    @Test
    void shouldTreatComplexSnippetAsHeuristicEstimate() {
        SyntaxCheckResult result = engine.check("for (int i = 0; i <= n; i++) { sum += i; }", "java");

        assertThat(result.isCompileSuccess()).isTrue();
    }

    @Test
    void shouldBoundSourceAndHandleNonJavaWithoutCompiling() {
        assertThat(engine.check(null, "java").getSuspiciousRegion()).isEqualTo("empty editor");
        assertThat(engine.check("x".repeat(20001), "java").getSuspiciousRegion()).isEqualTo("source size limit");
        assertThat(engine.check("print('hello')", "python3").isCompileSuccess()).isTrue();
        assertThat(engine.check("String x = \"value\";", "JAVA").isCompileSuccess()).isTrue();
        assertThat(engine.check("int x = new int[] {", "java").isCompileSuccess()).isTrue();
        assertThat(engine.check("int x = 1 }", "java").isCompileSuccess()).isTrue();
    }
}
