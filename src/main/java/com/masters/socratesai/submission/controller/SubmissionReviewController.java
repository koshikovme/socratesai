package com.masters.socratesai.submission.controller;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.submission.dto.*;
import com.masters.socratesai.submission.service.SubmissionReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequiredArgsConstructor @Validated
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class SubmissionReviewController {
    private final SubmissionReviewService service;

    @GetMapping("/api/teacher/submissions/{id}")
    public StaffSubmissionDetailResponse review(@PathVariable UUID id) { return service.review(id); }

    @GetMapping("/api/teacher/problems/{id}/submissions")
    public SubmissionResultsResponse problem(@PathVariable @Positive Long id, @Valid @ModelAttribute SubmissionResultsFilter filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.problem(id, filter, page, size);
    }

    @GetMapping("/api/teacher/collections/{id}/submissions")
    public SubmissionResultsResponse collection(@PathVariable @Positive Long id, @Valid @ModelAttribute SubmissionResultsFilter filter,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.collection(id, filter, page, size);
    }
}
