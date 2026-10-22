package com.masters.socratesai.task.service;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.execution.model.*;
import com.masters.socratesai.task.model.*;
import com.masters.socratesai.task.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class PublicationGate {
    private final ProblemLanguageConfigRepository languages;
    private final ProblemTestCaseRepository tests;
    private final ReferenceVerificationRepository verifications;
    private final ProblemFingerprint fingerprints;
    private final AuthoringRuntimeResolver runtimes;
    private final Clock clock;

    public void completeDraft(ProblemVersion version) {
        var cases = tests.findAllByProblemVersion_IdOrderByPosition(version.getId());
        if (version.getStatement().isBlank() || cases.stream().noneMatch(test -> !test.getHidden())
                || cases.stream().noneMatch(ProblemTestCase::getHidden)
                || languages.findAllByProblemVersion_IdOrderByLanguageKey(version.getId()).isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST);
        }
        runtimes.requireConfigured(languages.findAllByProblemVersion_IdOrderByLanguageKey(version.getId()));
    }

    public void verified(ProblemVersion version, UUID verificationId) {
        completeDraft(version);
        var verification = verifications.findById(verificationId).orElseThrow(() -> new ApiException(ApiErrorCode.VERIFICATION_REQUIRED));
        if (!verification.getProblemVersionId().equals(version.getId()) || verification.getStatus() != ExecutionStatus.FINISHED
                || verification.getVerdict() != ExecutionVerdict.ACCEPTED || !verification.getValidUntil().toInstant().isAfter(clock.instant())
                || !verification.getDraftHash().equals(fingerprints.hash(version))) throw new ApiException(ApiErrorCode.VERIFICATION_REQUIRED);
    }
}
