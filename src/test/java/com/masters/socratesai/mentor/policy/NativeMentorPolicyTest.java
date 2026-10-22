package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class NativeMentorPolicyTest {
    private final MentorPolicyProperties properties = new MentorPolicyProperties();
    private final MlPolicySelector ml = mock(MlPolicySelector.class);
    private final MentorPolicyService service = new MentorPolicyService(new RuleBasedPolicyEngine(properties), ml, new PolicyGuardrailService(), properties);
    private final AnalyzerResult analyzer = new AnalyzerResult();
    private final StudentContextDto history = new StudentContextDto();
    @BeforeEach void setUp() { properties.setMode(PolicyMode.ML); properties.getMl().setEnabled(true); properties.getMl().setMinConfidence(.35);
        analyzer.setErrorType("SYNTAX_ERROR"); history.setSameErrorCount(1); }
    @ParameterizedTest @ValueSource(strings = {"java", "cpp17", "javascript", "python", "Python 3", ""})
    void unsupportedOrNoncanonicalLanguagesNeverInvokeTheModel(String language) {
        var outcome = service.decideNative(analyzer, history, 0, "untrusted-source", language, true);
        assertThat(outcome.mode()).isEqualTo("RULE"); assertThat(outcome.fallbackReason()).isEqualTo("UNSUPPORTED_LANGUAGE");
        assertThat(outcome.decision().action()).isEqualTo(FeedbackAction.CODE_HIGHLIGHT); verifyNoInteractions(ml);
    }
    @Test void disabledAndEmptyInferenceUseTemplatesWithoutNetwork() {
        properties.getMl().setEnabled(false);
        assertThat(service.decideNative(analyzer, history, 0, "print(3)", "python3", false).fallbackReason()).isEqualTo("ML_DISABLED");
        properties.getMl().setEnabled(true);
        assertThat(service.decideNative(analyzer, history, 0, "", "python3", false).fallbackReason()).isEqualTo("EMPTY_SOURCE"); verifyNoInteractions(ml);
    }
    @Test void inferenceFailureFallsBackEvenWhenLegacyFallbackWasDisabled() {
        properties.getMl().setFallbackToRule(false);
        when(ml.decideWithMetadata(any(), eq("python3"), anyBoolean())).thenThrow(new IllegalStateException("private-source-token"));
        var result = service.decideNative(analyzer, history, 0, "print(3)", "python3", false);
        assertThat(result.mode()).isEqualTo("RULE"); assertThat(result.fallbackReason()).isEqualTo("ML_UNAVAILABLE");
        assertThat(result.toString()).doesNotContain("private-source-token");
    }
    @Test void lowConfidenceRetainsThePredictionButUsesARuleAction() {
        when(ml.decideWithMetadata(any(), eq("python3"), anyBoolean())).thenReturn(new PolicyDecision(FeedbackAction.NO_FEEDBACK, "model-v1", "accepted", .2));
        var result = service.decideNative(analyzer, history, 0, "print(3)", "python3", true);
        assertThat(result.mode()).isEqualTo("RULE"); assertThat(result.decision().mentorState()).isEqualTo("accepted");
        assertThat(result.decision().policyVersion()).isEqualTo("model-v1+rule-low-confidence");
        assertThat(result.decision().action()).isEqualTo(FeedbackAction.CODE_HIGHLIGHT);
    }
    @Test void eligibleConfidentPythonInferenceRemainsIntegrated() {
        when(ml.decideWithMetadata(any(), eq("python3"), anyBoolean())).thenReturn(new PolicyDecision(FeedbackAction.CONCEPTUAL_HINT, "model-v1", "semantic_debug", .8));
        assertThat(service.decideNative(analyzer, history, 1, "print(3)", "python3", true).mode()).isEqualTo("ML");
    }
    @Test void explicitAdministratorResearchAblationRetainsItsOriginalMeaning() {
        properties.setMode(PolicyMode.NO_POLICY);
        var result = service.decideForResearch(analyzer, history, 1, "synthetic research source", "java");
        assertThat(result.policyVersion()).isEqualTo("no-policy-v1"); assertThat(result.action()).isEqualTo(FeedbackAction.CONCEPTUAL_HINT);
        verifyNoInteractions(ml);
    }
}
