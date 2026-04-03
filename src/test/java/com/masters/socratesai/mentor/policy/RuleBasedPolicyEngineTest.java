package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedPolicyEngineTest {

    private RuleBasedPolicyEngine engine;

    @BeforeEach
    void setUp() {
        MentorPolicyProperties properties = new MentorPolicyProperties();
        properties.setRuleVersion("rule-test");
        engine = new RuleBasedPolicyEngine(properties);
    }

    @Test
    void shouldReturnCodeHighlightForFirstSyntaxError() {
        PolicyFeatures features = new PolicyFeatures("SYNTAX_ERROR", "HIGH", false, 0, 1, 1, 1, 1, null, null, true, 12, 0);

        assertThat(engine.decide(features)).isEqualTo(FeedbackAction.CODE_HIGHLIGHT);
    }

    @Test
    void shouldReturnConceptualHintForRepeatedOffByOne() {
        PolicyFeatures features = new PolicyFeatures("OFF_BY_ONE", "MEDIUM", true, 0, 1, 2, 2, 2, "CODE_HIGHLIGHT", false, true, 20, 1);

        assertThat(engine.decide(features)).isEqualTo(FeedbackAction.CONCEPTUAL_HINT);
    }

    @Test
    void shouldReturnGuidingQuestionForStuckNoProgress() {
        PolicyFeatures features = new PolicyFeatures("STUCK_NO_PROGRESS", "MEDIUM", true, 0, 1, 1, 1, 1, null, null, true, 15, 0);

        assertThat(engine.decide(features)).isEqualTo(FeedbackAction.GUIDING_QUESTION);
    }

    @Test
    void shouldReturnNoFeedbackWhenCompilationAndTestsSucceed() {
        PolicyFeatures features = new PolicyFeatures("SUCCESS", "LOW", true, 1, 0, 0, 0, 1, "CONCEPTUAL_HINT", true, false, 18, 1);

        assertThat(engine.decide(features)).isEqualTo(FeedbackAction.NO_FEEDBACK);
    }

    @Test
    void shouldDefaultToConceptualHintForUnknownState() {
        PolicyFeatures features = new PolicyFeatures("UNKNOWN", "MEDIUM", true, 0, 1, 1, 1, 1, null, null, true, 10, 0);

        assertThat(engine.decide(features)).isEqualTo(FeedbackAction.CONCEPTUAL_HINT);
    }
}
