package com.masters.socratesai.submission.service;

import com.masters.socratesai.submission.dto.SubmissionResponse;
import com.masters.socratesai.submission.model.Submission;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SubmissionProjection {
    public SubmissionResponse summary(Submission submission) {
        var snapshot = submission.getSnapshot();
        return new SubmissionResponse(submission.getId(), snapshot.getProblemId(), snapshot.getProblemVersionId(), snapshot.getLanguageKey(),
                snapshot.getRuntimeKey(), snapshot.getRuntimeVersion(), submission.getAttemptNumber(), submission.getStatus(), submission.getVerdict(),
                submission.getTestsTotal(), submission.getTestsPassed(), submission.getTestsFailed(), submission.getTimeConsumedMs(),
                submission.getMemoryConsumedBytes(), submission.getCreatedAt(), submission.getFinishedAt());
    }
}
