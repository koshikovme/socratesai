package com.masters.socratesai.interaction.controller;

import com.masters.socratesai.interaction.dto.InteractionResultResponse;
import com.masters.socratesai.interaction.dto.InteractionResultUpdateRequest;
import com.masters.socratesai.interaction.service.InteractionLogService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/interactions")
public class InteractionController {

    private final InteractionLogService interactionLogService;

    public InteractionController(InteractionLogService interactionLogService) {
        this.interactionLogService = interactionLogService;
    }

    @PostMapping("/{interactionId}/result")
    public ResponseEntity<InteractionResultResponse> updateResult(
            @PathVariable UUID interactionId,
            @RequestBody InteractionResultUpdateRequest request
    ) {
        return ResponseEntity.ok(
                interactionLogService.updateInteractionResult(
                        interactionId,
                        request.getResolvedAfterFeedback(),
                        request.getFixedAfterMs()
                )
        );
    }

    @GetMapping(value = "/policy-dataset", produces = "text/csv")
    public ResponseEntity<byte[]> exportPolicyDataset(
            @RequestParam(defaultValue = "false") boolean resolvedOnly
    ) {
        byte[] csv = interactionLogService.exportPolicyDataset(resolvedOnly);
        String fileName = resolvedOnly ? "policy_dataset_successful.csv" : "policy_dataset.csv";

        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .body(csv);
    }
}
