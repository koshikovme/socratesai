package com.masters.socratesai.interaction.service;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.interaction.dto.InteractionResultResponse;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InteractionLogServiceTest {

    private InteractionLogRepository repository;
    private InteractionLogService service;

    @BeforeEach
    void setUp() {
        repository = mock(InteractionLogRepository.class);
        service = new InteractionLogService(repository);
    }

    @Test
    void shouldSaveInteractionWithCalculatedLatencyAndDerivedFields() {
        UUID sessionId = UUID.randomUUID();
        AnalyzerResult analyzer = new AnalyzerResult();
        analyzer.setErrorType("WRONG_CONDITION");
        analyzer.setSeverity("MEDIUM");
        analyzer.setCompileSuccess(true);
        analyzer.setTestsPassed(0);
        analyzer.setTestsFailed(1);
        analyzer.setSuspiciousRegion("line 4");
        analyzer.setAnalysisTimeMs(120);
        analyzer.setCodeLines(18);

        StudentContextDto context = new StudentContextDto();
        context.setSameErrorCount(2);
        context.setTotalErrorsSeen(3);
        context.setLastFeedbackAction("CODE_HIGHLIGHT");
        context.setLastFeedbackSuccess(false);
        context.setTotalFeedbackCountInSession(1);

        when(repository.save(any(InteractionLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        InteractionLog saved = service.saveInteraction(
                sessionId,
                1L,
                2L,
                3,
                "System.out.println(1);",
                analyzer,
                context,
                FeedbackAction.CONCEPTUAL_HINT,
                "rule-v1",
                "Check the condition.",
                "template",
                30,
                40
        );

        assertThat(saved.getTotalLatencyMs()).isEqualTo(190);
        assertThat(saved.getFeedbackAction()).isEqualTo("CONCEPTUAL_HINT");
        assertThat(saved.getHasSuspiciousRegion()).isTrue();
        assertThat(saved.getCodeHash()).isNotBlank();
        assertThat(saved.getCodeLines()).isEqualTo(18);
    }

    @Test
    void shouldBuildStudentContextFromRecentSessionLogs() {
        UUID sessionId = UUID.randomUUID();
        InteractionLog latest = InteractionLog.builder()
                .errorType("WRONG_CONDITION")
                .feedbackAction("CONCEPTUAL_HINT")
                .resolvedAfterFeedback(false)
                .createdAt(OffsetDateTime.now())
                .build();
        latest.prePersist();

        InteractionLog previousSame = InteractionLog.builder()
                .errorType("WRONG_CONDITION")
                .feedbackAction("CODE_HIGHLIGHT")
                .resolvedAfterFeedback(false)
                .createdAt(OffsetDateTime.now().minusMinutes(1))
                .build();
        previousSame.prePersist();

        InteractionLog earlierDifferent = InteractionLog.builder()
                .errorType("SYNTAX_ERROR")
                .feedbackAction("CODE_HIGHLIGHT")
                .resolvedAfterFeedback(true)
                .createdAt(OffsetDateTime.now().minusMinutes(2))
                .build();
        earlierDifferent.prePersist();

        when(repository.findTop20BySessionIdOrderByCreatedAtDesc(sessionId)).thenReturn(List.of(latest, previousSame, earlierDifferent));

        StudentContextDto context = service.buildStudentContext(sessionId, 1L, 2L, "WRONG_CONDITION");

        assertThat(context.getSameErrorCount()).isEqualTo(3);
        assertThat(context.getTotalErrorsSeen()).isEqualTo(3);
        assertThat(context.getLastFeedbackAction()).isEqualTo("CONCEPTUAL_HINT");
        assertThat(context.getLastFeedbackSuccess()).isFalse();
        assertThat(context.getTotalFeedbackCountInSession()).isEqualTo(3);
    }

    @Test
    void shouldExportCsvWithEscapedSuspiciousRegion() {
        InteractionLog row = InteractionLog.builder()
                .interactionId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
                .studentId(1L)
                .taskId(2L)
                .policyVersion("rule-v1")
                .createdAt(OffsetDateTime.parse("2026-04-03T10:15:30+05:00"))
                .errorType("WRONG_CONDITION")
                .severity("MEDIUM")
                .compileSuccess(true)
                .testsPassed(0)
                .testsFailed(1)
                .sameErrorCount(2)
                .totalErrorsSeen(3)
                .attemptNo(2)
                .lastFeedbackAction("CODE_HIGHLIGHT")
                .lastFeedbackSuccess(false)
                .hasSuspiciousRegion(true)
                .codeLines(20)
                .totalFeedbackCountInSession(1)
                .feedbackAction("CONCEPTUAL_HINT")
                .resolvedAfterFeedback(false)
                .fixedAfterMs(1500)
                .suspiciousRegion("line 4, token \"if\"")
                .build();
        row.prePersist();

        when(repository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(row));

        byte[] bytes = service.exportPolicyDataset(false);
        String csv = new String(bytes, StandardCharsets.UTF_8);

        assertThat(csv).contains("interaction_id,student_id,task_id");
        assertThat(csv).contains("\"line 4, token \"\"if\"\"\"");
    }

    @Test
    void shouldUpdateInteractionResult() {
        UUID interactionId = UUID.randomUUID();
        InteractionLog log = InteractionLog.builder()
                .interactionId(interactionId)
                .resolvedAfterFeedback(false)
                .build();
        log.prePersist();

        when(repository.findById(interactionId)).thenReturn(Optional.of(log));

        InteractionResultResponse response = service.updateInteractionResult(interactionId, true, 900);

        assertThat(response.getInteractionId()).isEqualTo(interactionId);
        assertThat(response.getResolvedAfterFeedback()).isTrue();
        assertThat(response.getFixedAfterMs()).isEqualTo(900);
    }
}
