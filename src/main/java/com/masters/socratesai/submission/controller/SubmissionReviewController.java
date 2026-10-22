package com.masters.socratesai.submission.controller;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.submission.dto.SubmissionDetailResponse;
import com.masters.socratesai.submission.repo.SubmissionRepository;
import com.masters.socratesai.submission.service.SubmissionProjection;
import com.masters.socratesai.task.service.ProblemAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/teacher/submissions") @RequiredArgsConstructor
public class SubmissionReviewController {
    private final SubmissionRepository submissions;
    private final ProblemAccess access;
    @GetMapping("/{id}") @Transactional(readOnly = true)
    public SubmissionDetailResponse review(@PathVariable UUID id) {
        var submission = submissions.findById(id).orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND));
        access.owned(submission.getSnapshot().getProblemId(), false);
        return new SubmissionDetailResponse(SubmissionProjection.summary(submission), submission.getSnapshot().getSourceCode());
    }
}
