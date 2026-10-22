package com.masters.socratesai.task.controller;

import com.masters.socratesai.task.dto.*;
import com.masters.socratesai.task.verification.ReferenceVerificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/teacher/problems/{problemId}") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
public class ReferenceVerificationController {
    private final ReferenceVerificationService service;
    @PostMapping("/verify") @ResponseStatus(HttpStatus.ACCEPTED)
    public ReferenceVerificationResponse request(@PathVariable @Positive Long problemId, @RequestBody @Valid ReferenceVerificationRequest request) {
        return service.request(problemId, request);
    }
    @GetMapping("/verifications/{id}")
    public ReferenceVerificationResponse get(@PathVariable @Positive Long problemId, @PathVariable UUID id) { return service.get(problemId, id); }
}
