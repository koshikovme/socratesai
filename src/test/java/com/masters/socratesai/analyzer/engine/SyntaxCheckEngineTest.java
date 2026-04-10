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
    void shouldAcceptValidJavaSnippet() {
        SyntaxCheckResult result = engine.check("int x = 1;", "java");

        assertThat(result.isCompileSuccess()).isTrue();
        assertThat(result.getSuspiciousRegion()).isNull();
    }
}
