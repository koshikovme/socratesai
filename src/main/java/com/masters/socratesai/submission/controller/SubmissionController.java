package com.masters.socratesai.submission.controller;

import com.masters.socratesai.common.api.PageResponse;
import com.masters.socratesai.submission.dto.*;
import com.masters.socratesai.submission.service.SubmissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController @RequiredArgsConstructor @Validated
public class SubmissionController {
    private final SubmissionService service;
    @PostMapping("/api/problems/{id}/submissions")
    public ResponseEntity<SubmissionResponse> submit(@PathVariable @Positive Long id, @RequestBody @Valid CreateSubmissionRequest request) {
        var response = service.submit(id, request);
        return ResponseEntity.accepted().location(URI.create("/api/submissions/" + response.id())).body(response);
    }
    @GetMapping("/api/submissions/{id}")
    public SubmissionStatusResponse status(@PathVariable UUID id) { return service.status(id); }
    @GetMapping("/api/submissions")
    public PageResponse<SubmissionResponse> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.history(page, size);
    }
}
