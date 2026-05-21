package com.masters.socratesai.interaction.service;

import com.masters.socratesai.interaction.dto.ExpertAgreementResponse;
import com.masters.socratesai.interaction.dto.ExpertLabelRequest;
import com.masters.socratesai.interaction.dto.ExpertLabelResponse;
import com.masters.socratesai.interaction.model.ExpertActionLabel;
import com.masters.socratesai.interaction.model.InteractionLog;
import com.masters.socratesai.interaction.repo.ExpertActionLabelRepository;
import com.masters.socratesai.interaction.repo.InteractionLogRepository;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExpertLabelServiceTest {

    private InteractionLogRepository interactionLogRepository;
    private ExpertActionLabelRepository expertActionLabelRepository;
    private ExpertLabelService service;

    @BeforeEach
    void setUp() {
        interactionLogRepository = mock(InteractionLogRepository.class);
        expertActionLabelRepository = mock(ExpertActionLabelRepository.class);
        service = new ExpertLabelService(interactionLogRepository, expertActionLabelRepository);
    }

    @Test
    void shouldCreateExpertLabelForInteraction() {
        UUID interactionId = UUID.randomUUID();
        InteractionLog interaction = InteractionLog.builder()
                .interactionId(interactionId)
                .feedbackAction("CODE_HIGHLIGHT")
                .errorType("OFF_BY_ONE")
                .build();
        interaction.prePersist();

        ExpertLabelRequest request = new ExpertLabelRequest();
        request.setReviewerId(42L);
        request.setTargetFeedbackAction(FeedbackAction.CONCEPTUAL_HINT);
        request.setConfidence(4);
        request.setRationale("Student repeated the same conceptual bug.");

        when(interactionLogRepository.findById(interactionId)).thenReturn(Optional.of(interaction));
        when(expertActionLabelRepository.findByInteractionInteractionIdAndReviewerId(interactionId, 42L))
                .thenReturn(Optional.empty());
        when(expertActionLabelRepository.save(any(ExpertActionLabel.class))).thenAnswer(invocation -> {
            ExpertActionLabel label = invocation.getArgument(0);
            label.prePersist();
            return label;
        });

        ExpertLabelResponse response = service.upsertLabel(interactionId, request);

        assertThat(response.getInteractionId()).isEqualTo(interactionId);
        assertThat(response.getReviewerId()).isEqualTo(42L);
        assertThat(response.getTargetFeedbackAction()).isEqualTo("CONCEPTUAL_HINT");
        assertThat(response.getConfidence()).isEqualTo(4);
        assertThat(response.getLabelId()).isNotNull();
    }

    @Test
    void shouldCalculateCohensKappaForTwoExpertReviewers() {
        InteractionLog firstInteraction = interaction(UUID.randomUUID());
        InteractionLog secondInteraction = interaction(UUID.randomUUID());
        InteractionLog thirdInteraction = interaction(UUID.randomUUID());

        when(expertActionLabelRepository.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(
                label(firstInteraction, 1L, "CODE_HIGHLIGHT"),
                label(firstInteraction, 2L, "CODE_HIGHLIGHT"),
                label(secondInteraction, 1L, "CONCEPTUAL_HINT"),
                label(secondInteraction, 2L, "GUIDING_QUESTION"),
                label(thirdInteraction, 1L, "NO_FEEDBACK")
        ));

        ExpertAgreementResponse response = service.calculateAgreement();

        assertThat(response.getLabeledInteractions()).isEqualTo(3);
        assertThat(response.getOverlappingInteractions()).isEqualTo(2);
        assertThat(response.getObservedAgreement()).isEqualTo(0.5);
        assertThat(response.getExpectedAgreement()).isEqualTo(0.25);
        assertThat(response.getCohenKappa()).isEqualTo(0.3333);
    }

    private InteractionLog interaction(UUID id) {
        InteractionLog interaction = InteractionLog.builder()
                .interactionId(id)
                .feedbackAction("CODE_HIGHLIGHT")
                .errorType("UNKNOWN")
                .createdAt(OffsetDateTime.now())
                .build();
        interaction.prePersist();
        return interaction;
    }

    private ExpertActionLabel label(InteractionLog interaction, Long reviewerId, String action) {
        ExpertActionLabel label = ExpertActionLabel.builder()
                .labelId(UUID.randomUUID())
                .interaction(interaction)
                .reviewerId(reviewerId)
                .targetFeedbackAction(action)
                .createdAt(OffsetDateTime.now())
                .build();
        label.prePersist();
        return label;
    }
}
