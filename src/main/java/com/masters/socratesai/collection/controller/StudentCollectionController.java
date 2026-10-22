package com.masters.socratesai.collection.controller;

import com.masters.socratesai.collection.dto.*;
import com.masters.socratesai.collection.service.StudentCollectionService;
import com.masters.socratesai.common.api.PageResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/collections") @RequiredArgsConstructor
public class StudentCollectionController {
    private final StudentCollectionService service;
    private final com.masters.socratesai.collection.service.CollectionProgressService progress;
    @GetMapping("/{id}/progress") public CollectionProgressResponse progress(@PathVariable @Positive Long id) { return progress.get(id); }
    @GetMapping public PageResponse<StudentCollectionSummaryResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
    @GetMapping("/{id}") public StudentCollectionResponse get(@PathVariable @Positive Long id) { return service.get(id); }
}
