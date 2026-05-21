package com.masters.socratesai.interaction.repo;

import com.masters.socratesai.interaction.model.ExpertActionLabel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExpertActionLabelRepository extends JpaRepository<ExpertActionLabel, UUID> {

    Optional<ExpertActionLabel> findByInteractionInteractionIdAndReviewerId(UUID interactionId, Long reviewerId);

    List<ExpertActionLabel> findByInteractionInteractionIdOrderByReviewerIdAsc(UUID interactionId);

    List<ExpertActionLabel> findAllByOrderByCreatedAtAsc();
}
