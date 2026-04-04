package com.masters.socratesai.interaction.service;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.masters.socratesai.support.JpaIntegrationTestConfig;

import static org.assertj.core.api.Assertions.assertThat;

@SpringJUnitConfig(JpaIntegrationTestConfig.class)
@Transactional
class InteractionLogServiceTransactionalTest {

    @Autowired
    private InteractionLogService service;

    @Autowired
    private InteractionLogRepository repository;

    @Test
    void shouldPersistResolvedOutcomeInsideTransactionalUpdate() {
        InteractionLog saved = service.saveInteraction(
                UUID.randomUUID(),
                7L,
                8L,
                1,
                "if (x < 0) {}",
                analyzerResult("SYNTAX_ERROR"),
                context(),
                FeedbackAction.CODE_HIGHLIGHT,
                "rule-v1",
                "Check line 1",
                "template",
                5,
                10
        );

        service.updateInteractionResult(saved.getInteractionId(), true, 900);

        InteractionLog updated = repository.findById(saved.getInteractionId()).orElseThrow();
        assertThat(updated.getResolvedAfterFeedback()).isTrue();
        assertThat(updated.getFixedAfterMs()).isEqualTo(900);
    }

    @Test
    void shouldExportOnlyResolvedRowsWhenResolvedOnlyEnabled() {
        InteractionLog resolved = service.saveInteraction(
                UUID.randomUUID(), 10L, 11L, 1, "code-a", analyzerResult("WRONG_CONDITION"),
                context(), FeedbackAction.CONCEPTUAL_HINT, "rule-v1", "Hint A", "template", 6, 11
        );
        InteractionLog unresolved = service.saveInteraction(
                UUID.randomUUID(), 10L, 11L, 2, "code-b", analyzerResult("OFF_BY_ONE"),
                context(), FeedbackAction.GUIDING_QUESTION, "rule-v1", "Hint B", "template", 7, 12
        );
        service.updateInteractionResult(resolved.getInteractionId(), true, 700);

        byte[] exported = service.exportPolicyDataset(true);
        String csv = new String(exported, StandardCharsets.UTF_8);

        assertThat(csv).contains(resolved.getInteractionId().toString());
        assertThat(csv).doesNotContain(unresolved.getInteractionId().toString());
    }

    private AnalyzerResult analyzerResult(String errorType) {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType(errorType);
        result.setSeverity("MEDIUM");
        result.setCompileSuccess(false);
        result.setTestsPassed(0);
        result.setTestsFailed(1);
        result.setSuspiciousRegion("line 1");
        result.setAnalysisTimeMs(20);
        result.setCodeLines(5);
        return result;
    }

    private StudentContextDto context() {
        StudentContextDto dto = new StudentContextDto();
        dto.setSameErrorCount(1);
        dto.setTotalErrorsSeen(1);
        dto.setLastFeedbackAction("CODE_HIGHLIGHT");
        dto.setLastFeedbackSuccess(false);
        dto.setTotalFeedbackCountInSession(1);
        return dto;
    }
}
