package com.masters.socratesai.collection.controller;

import com.masters.socratesai.collection.dto.*;
import com.masters.socratesai.collection.repo.projection.CollectionSummary;
import com.masters.socratesai.collection.service.TeacherCollectionService;
import com.masters.socratesai.common.api.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/teacher/collections") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class TeacherCollectionController {
    private final TeacherCollectionService service;
    @GetMapping public PageResponse<CollectionSummary> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
    @GetMapping("/{id}") public CollectionResponse get(@PathVariable @Positive Long id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public CollectionResponse create(@RequestBody @Valid CollectionDraftRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    public CollectionResponse update(@PathVariable @Positive Long id, @RequestBody @Valid CollectionDraftRequest request) { return service.update(id, request); }
    @PutMapping("/{id}/problems")
    public CollectionResponse members(@PathVariable @Positive Long id, @RequestBody @Valid CollectionMembershipRequest request) { return service.replace(id, request); }
    @PostMapping("/{id}/publish")
    public CollectionResponse publish(@PathVariable @Positive Long id, @RequestBody @Valid RevisionRequest request) { return service.publish(id, request.lockVersion()); }
    @PostMapping("/{id}/archive")
    public CollectionResponse archive(@PathVariable @Positive Long id, @RequestBody @Valid RevisionRequest request) { return service.archive(id, request.lockVersion()); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id, @RequestParam @PositiveOrZero Long lockVersion) { service.delete(id, lockVersion); }
}
