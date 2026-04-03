package com.masters.socratesai.session.repo;

import com.masters.socratesai.session.model.StudentTaskSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentTaskSessionRepository extends JpaRepository<StudentTaskSession, UUID> {
    Optional<StudentTaskSession> findByStudentIdAndTaskIdAndEndedAtIsNull(Long studentId, Long taskId);
}