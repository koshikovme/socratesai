package com.masters.socratesai.task.verification;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.domain.*;
import com.masters.socratesai.execution.model.ExecutionStatus;
import com.masters.socratesai.security.CurrentActor;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.repo.*;
import com.masters.socratesai.task.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.*;
import java.util.*;
import java.util.stream.*;

@Service @Validated @RequiredArgsConstructor
public class ReferenceVerificationService {
    private final ReferenceVerificationRepository verifications;
    private final ProblemVersionRepository versions;
    private final ProblemLanguageConfigRepository languages;
    private final ProblemTestCaseRepository tests;
    private final ProblemAccess access;
    private final PublicationGate publication;
    private final ProblemFingerprint fingerprints;
    private final CurrentActor actors;
    private final VerificationProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional
    public ReferenceVerificationResponse request(Long problemId, @Valid ReferenceVerificationRequest request) {
        var problem = access.owned(problemId, true);
        AuthoringValidation.revision(problem.getLockVersion(), request.lockVersion());
        var version = access.draft(problem);
        publication.completeDraft(version);
        var configs = languages.findAllByProblemVersion_IdOrderByLanguageKey(version.getId());
        var solutions = request.solutions().stream().collect(Collectors.toMap(ReferenceSolutionRequest::languageKey, ReferenceSolutionRequest::sourceCode));
        if (!solutions.keySet().equals(configs.stream().map(config -> config.getLanguageKey()).collect(Collectors.toSet()))) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        solutions.values().forEach(source -> AuthoringValidation.text(source, 80000));
        var now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        verifications.expire(version.getId(), now);
        if (verifications.existsByProblemVersionIdAndStatusIn(version.getId(), List.of(ExecutionStatus.QUEUED, ExecutionStatus.RUNNING))) {
            throw new ApiException(ApiErrorCode.CONFLICT);
        }
        var cases = tests.findAllByProblemVersion_IdOrderByPosition(version.getId()).stream().map(test -> ExecutionTestCase.builder()
                .testCaseId(test.getId()).position(test.getPosition()).hidden(test.getHidden()).input(test.getInputData())
                .expectedOutput(test.getExpectedOutput()).build()).toList();
        var record = verifications.saveAndFlush(ReferenceVerification.builder().id(UUID.randomUUID()).problemVersionId(version.getId())
                .requestedBy(actors.staff().id()).draftHash(fingerprints.hash(version)).status(ExecutionStatus.QUEUED)
                .testsTotal(cases.size() * configs.size()).deadlineAt(now.plus(properties.getTimeout())).validUntil(now.plus(properties.getEvidenceTtl())).build());
        var batches = configs.stream().flatMap(config -> IntStream.range(0, (cases.size() + properties.getBatchSize() - 1) / properties.getBatchSize())
                .mapToObj(index -> ExecutionBatch.builder().languageKey(config.getLanguageKey()).runtimeKey(config.getRuntimeKey())
                        .runtimeVersion(config.getRuntimeVersion()).sourceCode(solutions.get(config.getLanguageKey()))
                        .limits(ExecutionLimits.builder().timeLimitMs(config.getTimeLimitMs()).wallTimeLimitMs(config.getWallTimeLimitMs())
                                .memoryLimitKb(config.getMemoryLimitKb()).outputLimitBytes(config.getOutputLimitBytes()).build())
                        .comparison(version.getComparisonMode()).deadline(record.getDeadlineAt().toInstant())
                        .tests(cases.subList(index * properties.getBatchSize(), Math.min(cases.size(), (index + 1) * properties.getBatchSize()))).build())).toList();
        events.publishEvent(new ReferenceWork(record.getId(), batches, record.getDeadlineAt().toInstant()));
        return response(record);
    }

    @Transactional
    public ReferenceVerificationResponse get(Long problemId, UUID id) {
        access.owned(problemId, false);
        var record = verifications.lockById(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        if (versions.findByIdAndProblem_Id(record.getProblemVersionId(), problemId).isEmpty()) throw new ApiException(ApiErrorCode.NOT_FOUND);
        if ((record.getStatus() == ExecutionStatus.QUEUED || record.getStatus() == ExecutionStatus.RUNNING)
                && !record.getDeadlineAt().toInstant().isAfter(clock.instant())) {
            record.setStatus(ExecutionStatus.SYSTEM_ERROR); record.setFailureCode(SandboxFailureCode.DEADLINE_EXCEEDED.name());
            record.setFinishedAt(OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        }
        return response(record);
    }

    private ReferenceVerificationResponse response(ReferenceVerification record) {
        return new ReferenceVerificationResponse(record.getId(), record.getProblemVersionId(), record.getStatus(), record.getVerdict(),
                record.getTestsTotal(), record.getTestsPassed(), record.getFailureCode(), record.getValidUntil());
    }
}
