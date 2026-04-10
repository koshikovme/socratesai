package com.masters.socratesai.mentor.feedback;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.feedback.gemini.GeminiFeedbackService;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FeedbackGenerationServiceTest {

    private final FeedbackTemplateService templateService = mock(FeedbackTemplateService.class);
    private final GeminiFeedbackService geminiFeedbackService = mock(GeminiFeedbackService.class);

    private final FeedbackGenerationService service = new FeedbackGenerationService(
            templateService,
            geminiFeedbackService
    );

    @Test
    void shouldUseGeminiWhenEnabledAndSuccessful() {
        AnalyzerResult analyzer = analyzer();

        when(geminiFeedbackService.isEnabled()).thenReturn(true);
        when(geminiFeedbackService.generateWithGemini(
                FeedbackAction.CONCEPTUAL_HINT,
                analyzer,
                "code",
                "task"
        )).thenReturn("Gemini answer");

        String result = service.generate(FeedbackAction.CONCEPTUAL_HINT, analyzer, "code", "task");

        assertThat(result).isEqualTo("Gemini answer");
        assertThat(service.getSource()).isEqualTo("gemini");
    }

    @Test
    void shouldFallbackToTemplateWhenGeminiFails() {
        AnalyzerResult analyzer = analyzer();

        when(geminiFeedbackService.isEnabled()).thenReturn(true);
        when(geminiFeedbackService.generateWithGemini(
                FeedbackAction.CODE_HIGHLIGHT,
                analyzer,
                "code",
                "task"
        )).thenThrow(new IllegalStateException("provider error"));
        when(templateService.generate(FeedbackAction.CODE_HIGHLIGHT, analyzer)).thenReturn("Template fallback");

        String result = service.generate(FeedbackAction.CODE_HIGHLIGHT, analyzer, "code", "task");

        assertThat(result).isEqualTo("Template fallback");
        assertThat(service.getSource()).isEqualTo("template");
    }

    @Test
    void shouldUseTemplateWhenGeminiDisabled() {
        AnalyzerResult analyzer = analyzer();

        when(geminiFeedbackService.isEnabled()).thenReturn(false);
        when(templateService.generate(FeedbackAction.GUIDING_QUESTION, analyzer)).thenReturn("Template only");

        String result = service.generate(FeedbackAction.GUIDING_QUESTION, analyzer, "code", "task");

        assertThat(result).isEqualTo("Template only");
        assertThat(service.getSource()).isEqualTo("template");
    }

    private AnalyzerResult analyzer() {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType("OFF_BY_ONE");
        result.setCompileSuccess(true);
        result.setTestsPassed(0);
        result.setTestsFailed(1);
        result.setSuspiciousRegion("line 3");
        return result;
    }
}
