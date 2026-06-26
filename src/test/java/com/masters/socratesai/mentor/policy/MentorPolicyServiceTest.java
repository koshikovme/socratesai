package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
        when(mlSelector.decideWithMetadata(any())).thenThrow(new IllegalStateException("predictor down"));

        PolicyDecision decision = service.decide(analyzer("STUCK_NO_PROGRESS", true, 0, 1), context(1, 1, null, null, 0), 1);

        assertThat(decision.action()).isEqualTo(FeedbackAction.GUIDING_QUESTION);
        assertThat(decision.policyVersion()).isEqualTo("rule-test");
    }

    @Test
    void shouldFallbackToRuleWhenMlConfidenceIsLowButKeepMentorStateMetadata() {
        properties.setMode(PolicyMode.ML);
        properties.getMl().setFallbackToRule(true);
        properties.getMl().setMinConfidence(0.50);
        when(mlSelector.decideWithMetadata(any())).thenReturn(new PolicyDecision(
                FeedbackAction.CONCEPTUAL_HINT,
                "codeforces-source-v1",
                "semantic_debug",
                0.31
        ));

        PolicyDecision decision = service.decide(analyzer("STUCK_NO_PROGRESS", true, 0, 1), context(1, 1, null, null, 0), 1);

        assertThat(decision.action()).isEqualTo(FeedbackAction.GUIDING_QUESTION);
        assertThat(decision.policyVersion()).isEqualTo("codeforces-source-v1+rule-low-confidence");
        assertThat(decision.mentorState()).isEqualTo("semantic_debug");
        assertThat(decision.confidence()).isEqualTo(0.31);
    }

    @Test
    void shouldUseMlDecisionWhenConfidenceMeetsThreshold() {
        properties.setMode(PolicyMode.ML);
        properties.getMl().setMinConfidence(0.50);
        when(mlSelector.decideWithMetadata(any())).thenReturn(new PolicyDecision(
                FeedbackAction.CODE_HIGHLIGHT,
                "codeforces-source-v1",
                "syntax_repair",
                0.82
        ));

        PolicyDecision decision = service.decide(analyzer("WRONG_CONDITION", true, 0, 1), context(1, 1, null, null, 0), 1);

        assertThat(decision.action()).isEqualTo(FeedbackAction.CODE_HIGHLIGHT);
        assertThat(decision.policyVersion()).isEqualTo("codeforces-source-v1");
        assertThat(decision.mentorState()).isEqualTo("syntax_repair");
        assertThat(decision.confidence()).isEqualTo(0.82);
    }

    @Test
    void shouldThrowWhenMlFailsAndFallbackDisabled() {
        properties.setMode(PolicyMode.ML);
        properties.getMl().setFallbackToRule(false);
        when(mlSelector.decideWithMetadata(any())).thenThrow(new IllegalStateException("predictor down"));

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

    @Test
    void shouldSendSnakeCasePayloadToMlPolicyApi() {
        PolicyFeatures features = PolicyFeatures.from(
                analyzer("OFF_BY_ONE", true, 1, 2),
                context(2, 3, "CODE_HIGHLIGHT", false, 4),
                5
        );

        Map<String, Object> payload = MlPolicySelector.toPayload(features);

        assertThat(payload).containsEntry("error_type", "OFF_BY_ONE");
        assertThat(payload).containsEntry("compile_success", true);
        assertThat(payload).containsEntry("tests_passed", 1);
        assertThat(payload).containsEntry("passed_test_count", 1);
        assertThat(payload).containsEntry("same_error_count", 2);
        assertThat(payload).containsEntry("total_errors_seen", 3);
        assertThat(payload).containsEntry("attempt_no", 5);
        assertThat(payload).containsEntry("last_feedback_action", "CODE_HIGHLIGHT");
        assertThat(payload).containsEntry("last_feedback_success", false);
        assertThat(payload).containsEntry("has_suspicious_region", true);
        assertThat(payload).containsEntry("code_lines", 12);
        assertThat(payload).containsEntry("total_feedback_count_in_session", 4);
        assertThat(payload).doesNotContainKey("compileSuccess");
        assertThat(payload).doesNotContainKey("testsPassed");
        assertThat(payload).doesNotContainKey("sameErrorCount");

        String json = MlPolicySelector.toJsonPayload(features);
        assertThat(json).contains("\"compile_success\":true");
        assertThat(json).contains("\"tests_passed\":1");
        assertThat(json).contains("\"same_error_count\":2");
        assertThat(json).doesNotContain("compileSuccess");
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
