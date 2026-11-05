package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.mentor.feedback.*;
import com.masters.socratesai.mentor.feedback.gemini.GeminiFeedbackService;
import com.masters.socratesai.mentor.model.FeedbackAction;
import com.masters.socratesai.mentor.policy.*;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class NativeMentorServiceTest {
    private final NativeMentorStore store = mock(NativeMentorStore.class);
    private final MentorPolicyService policy = mock(MentorPolicyService.class);
    private final GeminiFeedbackService gemini = mock(GeminiFeedbackService.class);
    private final NativeMentorService service = new NativeMentorService(store, policy, gemini);
    private final MentorIdentity actor = MentorIdentity.builder().studentId(42L).build();
    private final PreparedMentorContext prepared = PreparedMentorContext.builder().evidence(MentorEvidence.unknown())
            .history(new StudentContextDto()).sourceHash("hash").publicStatement("Read two numbers and print the sum").build();

    @BeforeEach void setup() { when(store.prepare(any(), any())).thenReturn(prepared); }

    @Test void javaCodeGetsAGeminiHintBeforeAnyRunAndPreservesUnknownExecution() {
        var selected = new NativePolicyDecision(new PolicyDecision(FeedbackAction.CONCEPTUAL_HINT, "gemini-native-v1"), "GEMINI", "MULTILINGUAL_CLASSIFICATION");
        when(policy.decideNative(any(), any(), any(), anyString(), eq("java"), eq(false))).thenReturn(selected);
        when(gemini.generateNativeHint(eq(42L), eq("java"), anyString(), eq(prepared.getPublicStatement()), any(), eq(selected)))
                .thenReturn(new NativeHint("Add the Main entry point and call your method from it.", "execution_safety", FeedbackAction.CODE_HIGHLIGHT));
        var result = service.compute(actor, message("java", 1)).getFeedback();
        assertThat(result.getFeedbackSource()).isEqualTo("gemini");
        assertThat(result.getPolicyMode()).isEqualTo("GEMINI");
        assertThat(result.getFeedbackText()).contains("Main entry point");
        assertThat(result.isExecutionKnown()).isFalse(); assertThat(result.getCompileSuccess()).isNull();
        assertThat(result.getMentorStateConfidence()).isNull();
    }

    @Test void pythonUsesItsClassifierAndGeminiForSourceOnlyFeedback() {
        var selected = new NativePolicyDecision(new PolicyDecision(FeedbackAction.GUIDING_QUESTION, "classifier", "semantic_debug", .8), "ML", null);
        when(policy.decideNative(any(), any(), any(), anyString(), eq("python3"), eq(false))).thenReturn(selected);
        when(gemini.generateNativeHint(any(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(new NativeHint("Which value does your print expression use?", "syntax_repair", FeedbackAction.CONCEPTUAL_HINT));
        var result = service.compute(actor, message("python3", 1)).getFeedback();
        assertThat(result.getPolicyMode()).isEqualTo("ML");
        assertThat(result.getMentorState()).isEqualTo("semantic_debug");
        assertThat(result.getMentorStateConfidence()).isEqualTo(.8);
        assertThat(result.getAction()).isEqualTo("GUIDING_QUESTION");
        assertThat(result.getFeedbackSource()).isEqualTo("gemini");
    }

    @Test void simplyOpeningTheStarterDoesNotSpendTheStudentsAiAllowance() {
        var result = service.compute(actor, message("java", 0)).getFeedback();
        assertThat(result.getPolicyMode()).isEqualTo("READY");
        assertThat(result.getFeedbackSource()).isEqualTo("onboarding");
        assertThat(result.getFeedbackText()).contains("function marked TODO").doesNotContain("in solve");
        verifyNoInteractions(policy, gemini);
    }

    @Test void anAlreadyAcceptedGradedSubmissionDoesNotSpendAllowanceInAnyLanguage() {
        var accepted = MentorEvidence.builder().executionKnown(true).origin(com.masters.socratesai.common.model.EvidenceOrigin.JUDGED_SUBMISSION)
                .verdict(com.masters.socratesai.execution.model.ExecutionVerdict.ACCEPTED).compileSuccess(true).testsPassed(2).testsFailed(0).build();
        when(store.prepare(any(), any())).thenReturn(PreparedMentorContext.builder().evidence(accepted).sourceHash("accepted-hash").build());
        var feedback = service.compute(actor, message("java", 4)).getFeedback();
        assertThat(feedback.getPolicyMode()).isEqualTo("JUDGED");
        assertThat(feedback.getAction()).isEqualTo("NO_FEEDBACK");
        verifyNoInteractions(policy, gemini);
    }

    private CodeUpdateMessage message(String language, long revision) {
        return CodeUpdateMessage.builder().problemId(1L).problemVersionId(2L).languageKey(language).editorSessionId(UUID.randomUUID())
                .codeRevision(revision).code("student buffer").build();
    }
}
