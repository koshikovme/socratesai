package com.masters.socratesai.submission.dto;

import java.util.List;
import lombok.Builder;

/** Existing review fields stay compatible; private source is loaded only on explicit staff review. */
@Builder
public record StaffSubmissionDetailResponse(
        SubmissionResponse submission, String sourceCode, Student student, String problemTitle,
        Long collectionId, List<SubmissionStatusResponse.CaseSummary> cases
) {
    public record Student(Long id, String fullName, String email, String groupName) {}
    @Override public String toString() { return "StaffSubmissionDetailResponse[redacted]"; }
}
