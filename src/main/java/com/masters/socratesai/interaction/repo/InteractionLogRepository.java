package com.masters.socratesai.interaction.repo;

import com.masters.socratesai.interaction.model.InteractionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InteractionLogRepository extends JpaRepository<InteractionLog, UUID> {

    List<InteractionLog> findTop20BySessionIdOrderByCreatedAtDesc(UUID sessionId);

    List<InteractionLog> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<InteractionLog> findByStudentIdAndTaskIdOrderByCreatedAtDesc(Long studentId, Long taskId);

    List<InteractionLog> findAllByOrderByCreatedAtAsc();

    List<InteractionLog> findByResolvedAfterFeedbackTrueOrderByCreatedAtAsc();
}
