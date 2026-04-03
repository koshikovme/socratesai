package com.masters.socratesai.mentor.feedback;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;

@Service
public class FeedbackTemplateService {

    public String generate(FeedbackAction action, AnalyzerResult analyzer) {
        String errorType = analyzer.getErrorType();
        String region = analyzer.getSuspiciousRegion() == null ? "highlighted region" : analyzer.getSuspiciousRegion();

        return switch (action) {
            case CODE_HIGHLIGHT ->
                    "Check the " + region + ". There may be a small issue here.";

            case CONCEPTUAL_HINT -> switch (errorType) {
                case "OFF_BY_ONE" -> "Think about the loop boundaries. Are you iterating one step too far?";
                case "WRONG_CONDITION" -> "Review the condition carefully. Does it describe the exact stopping rule?";
                case "SYNTAX_ERROR" -> "Look closely at the syntax near the highlighted region.";
                default -> "Compare this part of your logic with the task requirement.";
            };

            case GUIDING_QUESTION ->
                    "What should be true at each iteration for your algorithm to work correctly?";

            case NO_FEEDBACK ->
                    "Good progress. Continue working.";
        };
    }
}