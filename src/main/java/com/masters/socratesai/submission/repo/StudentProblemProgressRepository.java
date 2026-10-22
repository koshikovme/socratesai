package com.masters.socratesai.submission.repo;

import com.masters.socratesai.submission.model.StudentProblemProgress;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface StudentProblemProgressRepository extends JpaRepository<StudentProblemProgress, Long> {
    Optional<StudentProblemProgress> findByStudentIdAndProblemId(Long studentId, Long problemId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from StudentProblemProgress p where p.studentId = :studentId and p.problemId = :problemId")
    Optional<StudentProblemProgress> lockProgress(Long studentId, Long problemId);
    List<StudentProblemProgress> findByStudentIdOrderByLastActivityAtDesc(Long studentId);
}
