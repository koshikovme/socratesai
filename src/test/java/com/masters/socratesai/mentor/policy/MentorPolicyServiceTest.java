package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentorPolicyServiceTest {

    private RuleBasedPolicyEngine ruleEngine;
    private MlPolicySelector mlSelector;
    private PolicyGuardrailService guardrailService;
    private MentorPolicyProperties properties;
    private MentorPolicyService service;

    @BeforeEach
    void setUp() {
        properties = new MentorPolicyProperties();
        properties.setRuleVersion("rule-test");
        ruleEngine = new RuleBasedPolicyEngine(properties);
        mlSelector = mock(MlPolicySelector.class);
        guardrailService = new PolicyGuardrailService();
        service = new MentorPolicyService(ruleEngine, mlSelector, guardrailService, properties);
    }

    @Test
    void shouldUseRuleEngineInRuleMode() {
        properties.setMode(PolicyMode.RULE);

        PolicyDecision decision = service.decide(analyzer("SYNTAX_ERROR", false, 0, 1), context(1, 1, null, null, 0), 1);

        assertThat(decision.action()).isEqualTo(FeedbackAction.CODE_HIGHLIGHT);
        assertThat(decision.policyVersion()).isEqualTo("rule-test");
    }

    @Test
    void shouldFallbackToRuleWhenMlFailsAndFallbackEnabled() {
        properties.setMode(PolicyMode.ML);
        properties.getMl().setFallbackToRule(true);
        when(mlSelector.decide(any())).thenThrow(new IllegalStateException("predictor down"));

        PolicyDecision decision = service.decide(analyzer("STUCK_NO_PROGRESS", true, 0, 1), context(1, 1, null, null, 0), 1);

        assertThat(decision.action()).isEqualTo(FeedbackAction.GUIDING_QUESTION);
        assertThat(decision.policyVersion()).isEqualTo("rule-test");
    }

    @Test
    void shouldThrowWhenMlFailsAndFallbackDisabled() {
        properties.setMode(PolicyMode.ML);
        properties.getMl().setFallbackToRule(false);
        when(mlSelector.decide(any())).thenThrow(new IllegalStateException("predictor down"));

        assertThatThrownBy(() -> service.decide(analyzer("WRONG_CONDITION", true, 0, 1), context(2, 2, null, null, 1), 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("predictor down");
    }

    @Test
    void shouldUseConceptualHintForNoPolicyBaseline() {
        properties.setMode(PolicyMode.NO_POLICY);

        PolicyDecision decision = service.decide(analyzer("SYNTAX_ERROR", false, 0, 0), context(1, 1, null, null, 0), 1);

        assertThat(decision.action()).isEqualTo(FeedbackAction.CONCEPTUAL_HINT);
        assertThat(decision.policyVersion()).isEqualTo("no-policy-v1");
    }

    private AnalyzerResult analyzer(String errorType, boolean compileSuccess, int testsPassed, int testsFailed) {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType(errorType);
        result.setSeverity("MEDIUM");
        result.setCompileSuccess(compileSuccess);
        result.setTestsPassed(testsPassed);
        result.setTestsFailed(testsFailed);
        result.setSuspiciousRegion("line 3");
        result.setCodeLines(12);
        return result;
    }

    private StudentContextDto context(int sameErrorCount, int totalErrorsSeen, String lastFeedbackAction, Boolean lastFeedbackSuccess, int totalFeedbackCount) {
        StudentContextDto dto = new StudentContextDto();
        dto.setSameErrorCount(sameErrorCount);
        dto.setTotalErrorsSeen(totalErrorsSeen);
        dto.setLastFeedbackAction(lastFeedbackAction);
        dto.setLastFeedbackSuccess(lastFeedbackSuccess);
        dto.setTotalFeedbackCountInSession(totalFeedbackCount);
        return dto;
    }
}
