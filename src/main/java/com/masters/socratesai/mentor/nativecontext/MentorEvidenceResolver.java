package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.common.model.EvidenceOrigin;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.execution.repo.*;
import com.masters.socratesai.websocket.dto.CodeUpdateMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;

@Component @RequiredArgsConstructor
public class MentorEvidenceResolver {
    private final ExecutionJobRepository jobs;
    private final ExecutionCaseResultRepository cases;
    private final Clock clock;
    public MentorEvidence resolve(Long studentId, CodeUpdateMessage message, String hash) {
        return jobs.latestMentorEvidence(studentId, message.getProblemId(), message.getProblemVersionId(), message.getLanguageKey(), hash,
                OffsetDateTime.now(clock)).map(job -> {
            var results = cases.mentorEvidence(job.getId());
            if (results.isEmpty() || results.stream().anyMatch(item -> item.status() != CaseExecutionStatus.FINISHED || item.verdict() == null))
                return MentorEvidence.unknown();
            var verdict = results.stream().map(MentorCaseEvidence::verdict).filter(value -> value != ExecutionVerdict.ACCEPTED).findFirst().orElse(ExecutionVerdict.ACCEPTED);
            int passed = (int) results.stream().filter(item -> item.verdict() == ExecutionVerdict.ACCEPTED).count();
            return MentorEvidence.builder().executionKnown(true).origin(job.getMode() == ExecutionMode.SUBMISSION ? EvidenceOrigin.JUDGED_SUBMISSION : EvidenceOrigin.SAMPLE_RUN)
                    .jobId(job.getId()).submissionId(job.getSubmissionId()).verdict(verdict).testsPassed(passed).testsFailed(results.size() - passed)
                    .compileSuccess(results.stream().noneMatch(item -> item.verdict() == ExecutionVerdict.COMPILATION_ERROR)).build();
        }).orElseGet(MentorEvidence::unknown);
    }
}
