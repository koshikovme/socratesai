package com.masters.socratesai.mentor.feedback;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeedbackTemplateServiceTest {

    private final FeedbackTemplateService service = new FeedbackTemplateService();

    @Test
    void shouldGenerateOffByOneConceptualHint() {
        AnalyzerResult analyzer = analyzer("OFF_BY_ONE", "line 5");

        String text = service.generate(FeedbackAction.CONCEPTUAL_HINT, analyzer);

        assertThat(text).contains("loop boundaries");
    }

    @Test
    void shouldGenerateCodeHighlightMessageUsingRegion() {
        AnalyzerResult analyzer = analyzer("SYNTAX_ERROR", "line 2");

        String text = service.generate(FeedbackAction.CODE_HIGHLIGHT, analyzer);

        assertThat(text).contains("line 2");
    }

    @Test
    void shouldGenerateNoFeedbackMessage() {
        AnalyzerResult analyzer = analyzer("SUCCESS", null);

        String text = service.generate(FeedbackAction.NO_FEEDBACK, analyzer);

        assertThat(text).isEqualTo("Good progress. Continue working.");
    }

    private AnalyzerResult analyzer(String errorType, String region) {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType(errorType);
        result.setSuspiciousRegion(region);
        return result;
    }
}
