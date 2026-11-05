package com.masters.socratesai.submission.dto;

import com.masters.socratesai.common.api.PageResponse;

public record SubmissionResultsResponse(PageResponse<StaffSubmissionResponse> submissions, Statistics summary) {
    public record Statistics(long attempts, long students, long accepted, long pending) {}
}
