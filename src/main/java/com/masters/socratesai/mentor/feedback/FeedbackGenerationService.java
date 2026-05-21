package com.masters.socratesai.mentor.feedback;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.feedback.gemini.GeminiFeedbackService;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;

@Service
public class FeedbackGenerationService {

    private final FeedbackTemplateService feedbackTemplateService;
    private final GeminiFeedbackService geminiFeedbackService;

    public FeedbackGenerationService(
            FeedbackTemplateService feedbackTemplateService,
            GeminiFeedbackService geminiFeedbackService
    ) {
        this.feedbackTemplateService = feedbackTemplateService;
        this.geminiFeedbackService = geminiFeedbackService;
    }

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
        if (geminiFeedbackService.isEnabled()) {
            try {
                String text = geminiFeedbackService.generateWithGemini(action, analyzer, code, taskSummary);
                return new GeneratedFeedback(text, "gemini");
            } catch (Exception e) {
                return new GeneratedFeedback(feedbackTemplateService.generate(action, analyzer), "template");
            }
        }

        return new GeneratedFeedback(feedbackTemplateService.generate(action, analyzer), "template");
    }
}
