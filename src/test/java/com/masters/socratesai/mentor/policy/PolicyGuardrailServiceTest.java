package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyGuardrailServiceTest {

    private final PolicyGuardrailService service = new PolicyGuardrailService();

    @Test
    void shouldReplaceNoFeedbackWithConceptualHintForRepeatedErrors() {
        PolicyFeatures features = new PolicyFeatures("WRONG_CONDITION", "MEDIUM", true, 0, 1, 3, 3, 3, "GUIDING_QUESTION", false, true, 14, 2);

        FeedbackAction result = service.applyGuards(FeedbackAction.NO_FEEDBACK, features);

        assertThat(result).isEqualTo(FeedbackAction.CONCEPTUAL_HINT);
    }

    @Test
    void shouldReplaceGuidingQuestionWithCodeHighlightForSyntaxError() {
        PolicyFeatures features = new PolicyFeatures("SYNTAX_ERROR", "HIGH", false, 0, 1, 1, 1, 1, null, null, true, 8, 0);

        FeedbackAction result = service.applyGuards(FeedbackAction.GUIDING_QUESTION, features);

        assertThat(result).isEqualTo(FeedbackAction.CODE_HIGHLIGHT);
    }
}
