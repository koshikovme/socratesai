package com.masters.socratesai.execution.controller;

import com.masters.socratesai.execution.dto.*;
import com.masters.socratesai.execution.service.SampleRunService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController @RequiredArgsConstructor @Validated
public class SampleRunController {
    private final SampleRunService service;
    @PostMapping("/api/problems/{id}/runs")
    public ResponseEntity<SampleRunStatusResponse> create(@PathVariable @Positive Long id, @RequestBody @Valid CreateExecutionRunRequest request) {
        var result = service.create(id, request);
        return ResponseEntity.accepted().location(URI.create("/api/runs/" + result.id())).body(result);
    }
    @GetMapping("/api/runs/{id}") public SampleRunStatusResponse get(@PathVariable UUID id) { return service.get(id); }
}
