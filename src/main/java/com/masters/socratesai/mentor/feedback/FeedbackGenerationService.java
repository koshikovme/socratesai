package com.masters.socratesai.mentor.feedback;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.feedback.gemini.GeminiFeedbackService;
import com.masters.socratesai.mentor.feedback.openai.OpenAiFeedbackService;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

@Service @RequiredArgsConstructor
public class FeedbackGenerationService {

    private final FeedbackTemplateService feedbackTemplateService;
    private final GeminiFeedbackService geminiFeedbackService;
    private final OpenAiFeedbackService openAiFeedbackService;

    public String generate(
            FeedbackAction action,
            AnalyzerResult analyzer,
            String code,
            String taskSummary
    ) {
        return generateWithMetadata(action, analyzer, code, taskSummary).text();
    }

    public GeneratedFeedback generateWithMetadata(
            FeedbackAction action,
            AnalyzerResult analyzer,
            String code,
            String taskSummary
    ) {
        return generate(null, action, analyzer, code, taskSummary);
    }

    public GeneratedFeedback generateNativeWithMetadata(Long studentId, FeedbackAction action, AnalyzerResult analyzer, String code, String taskSummary) {
        return generate(studentId, action, analyzer, code, taskSummary);
    }

    private GeneratedFeedback generate(Long studentId, FeedbackAction action, AnalyzerResult analyzer, String code, String taskSummary) {
        if (action == FeedbackAction.NO_FEEDBACK) return new GeneratedFeedback(feedbackTemplateService.generate(action, analyzer), "template");
        if (geminiFeedbackService.isEnabled()) {
            try {
                String text = studentId == null ? geminiFeedbackService.generateWithGemini(action, analyzer, code, taskSummary)
                        : geminiFeedbackService.generateForStudent(studentId, action, analyzer, code, taskSummary);
                if (valid(text)) return new GeneratedFeedback(text, "gemini");
            } catch (Exception e) {
                // Try the next configured provider before falling back to deterministic templates.
            }
        }

        if (openAiFeedbackService.isEnabled()) {
            try {
                String text = openAiFeedbackService.generateWithLlm(action, analyzer, taskSummary);
                if (valid(text)) return new GeneratedFeedback(text, "openai");
            } catch (Exception e) {
                // Fall through to deterministic templates.
            }
        }

        return new GeneratedFeedback(feedbackTemplateService.generate(action, analyzer), "template");
    }
    private boolean valid(String text) { return text != null && !text.isBlank() && text.length() <= 1200 && !text.contains("```"); }
}
