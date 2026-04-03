package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Component;

@Component
public class PolicyGuardrailService {

    public FeedbackAction applyGuards(FeedbackAction predicted, PolicyFeatures features) {
        if (features.sameErrorCount() >= 3 && predicted == FeedbackAction.NO_FEEDBACK) {
            return FeedbackAction.CONCEPTUAL_HINT;
        }

        if ("SYNTAX_ERROR".equals(features.errorType()) && predicted == FeedbackAction.GUIDING_QUESTION) {
            return FeedbackAction.CODE_HIGHLIGHT;
        }

        return predicted;
    }
}
