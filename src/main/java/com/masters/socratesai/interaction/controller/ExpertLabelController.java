package com.masters.socratesai.interaction.controller;

import com.masters.socratesai.interaction.dto.ExpertAgreementResponse;
import com.masters.socratesai.interaction.dto.ExpertLabelRequest;
import com.masters.socratesai.interaction.dto.ExpertLabelResponse;
import com.masters.socratesai.interaction.service.ExpertLabelService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/interactions")
public class ExpertLabelController {

    private final ExpertLabelService expertLabelService;

    public ExpertLabelController(ExpertLabelService expertLabelService) {
        this.expertLabelService = expertLabelService;
    }

    @PostMapping("/{interactionId}/expert-labels")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ExpertLabelResponse> upsertLabel(
            @PathVariable UUID interactionId,
            @Valid @RequestBody ExpertLabelRequest request
    ) {
        return ResponseEntity.ok(expertLabelService.upsertLabel(interactionId, request));
    }

    @GetMapping(value = "/expert-labels/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<byte[]> exportLabels() {
        byte[] csv = expertLabelService.exportLabelsCsv();
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expert_action_labels.csv\"")
                .body(csv);
    }

    @GetMapping("/expert-labels/agreement")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<ExpertAgreementResponse> agreement() {
        return ResponseEntity.ok(expertLabelService.calculateAgreement());
    }
}
