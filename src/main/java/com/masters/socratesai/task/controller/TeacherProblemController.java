package com.masters.socratesai.task.controller;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.repo.projection.ProblemSummary;
import com.masters.socratesai.task.service.TeacherProblemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/teacher/problems") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherProblemController {
    private final TeacherProblemService service;

    @GetMapping public PageResponse<ProblemSummary> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{id}") public ProblemAuthorResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ProblemAuthorResponse create(@RequestBody @Valid ProblemDraftRequest request) { return service.create(request); }
    @PutMapping("/{id}/draft")
    public ProblemAuthorResponse update(@PathVariable @Positive Long id, @RequestBody @Valid ProblemDraftRequest request) { return service.update(id, request); }
    @PostMapping("/{id}/draft")
    public ProblemAuthorResponse draft(@PathVariable @Positive Long id, @RequestBody @Valid RevisionRequest request) { return service.draft(id, request.lockVersion()); }
    @PostMapping("/{id}/publish")
    public ProblemAuthorResponse publish(@PathVariable @Positive Long id, @RequestBody @Valid PublishProblemRequest request) { return service.publish(id, request); }
    @PostMapping("/{id}/archive")
    public ProblemAuthorResponse archive(@PathVariable @Positive Long id, @RequestBody @Valid RevisionRequest request) { return service.archive(id, request.lockVersion()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long lockVersion) { service.delete(id, lockVersion, false); }
    @DeleteMapping("/{id}/draft") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long lockVersion) { service.delete(id, lockVersion, true); }
}
