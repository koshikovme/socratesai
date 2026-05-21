package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Component;

@Component
public class RuleBasedPolicyEngine implements PolicySelector {

    private final MentorPolicyProperties properties;

    public RuleBasedPolicyEngine(MentorPolicyProperties properties) {
        this.properties = properties;
    }

    @Override
    public FeedbackAction decide(PolicyFeatures features) {
        String errorType = features.errorType();
        int sameErrorCount = features.sameErrorCount();
        String lastAction = features.lastFeedbackAction();

        if ("SYNTAX_ERROR".equals(errorType) && sameErrorCount <= 1) {
            return FeedbackAction.CODE_HIGHLIGHT;
        }

        if ("OFF_BY_ONE".equals(errorType) && sameErrorCount == 1) {
            return FeedbackAction.CODE_HIGHLIGHT;
        }

        if ("WRONG_LOOP_BOUNDARY".equals(errorType) && sameErrorCount == 1) {
            return FeedbackAction.CODE_HIGHLIGHT;
        }

        if (("OFF_BY_ONE".equals(errorType) || "WRONG_LOOP_BOUNDARY".equals(errorType)) && sameErrorCount >= 2) {
            return FeedbackAction.CONCEPTUAL_HINT;
        }

        if ("WRONG_CONDITION".equals(errorType) && sameErrorCount >= 2) {
            return FeedbackAction.CONCEPTUAL_HINT;
        }

        if ("POSSIBLE_NULL_ACCESS".equals(errorType)) {
            return sameErrorCount <= 1 ? FeedbackAction.CODE_HIGHLIGHT : FeedbackAction.CONCEPTUAL_HINT;
        }

        if ("STUCK_NO_PROGRESS".equals(errorType)) {
            return FeedbackAction.GUIDING_QUESTION;
        }

        if ("GUIDING_QUESTION".equals(lastAction) && sameErrorCount >= 3) {
            return FeedbackAction.CONCEPTUAL_HINT;
        }

        if (features.compileSuccess() && features.testsFailed() == 0) {
            return FeedbackAction.NO_FEEDBACK;
        }

        return FeedbackAction.CONCEPTUAL_HINT;
    }

    @Override
    public String policyVersion() {
        return properties.getRuleVersion();
    }
}
