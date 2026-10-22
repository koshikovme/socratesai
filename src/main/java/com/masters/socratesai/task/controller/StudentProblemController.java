package com.masters.socratesai.task.controller;

import com.masters.socratesai.common.api.PageResponse;
import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.service.StudentProblemService;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/problems") @RequiredArgsConstructor
public class StudentProblemController {
    private final StudentProblemService service;

    @GetMapping public PageResponse<StudentProblemSummaryResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }
    @GetMapping("/{id}") public ProblemResponse get(@PathVariable @Positive Long id) { return service.get(id, null); }
    @GetMapping("/{id}/versions/{versionId}")
    public ProblemResponse version(@PathVariable @Positive Long id, @PathVariable @Positive Long versionId) { return service.get(id, versionId); }
}
