package com.masters.socratesai.interaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.interaction.dto.InteractionResultResponse;
import com.masters.socratesai.interaction.dto.InteractionResultUpdateRequest;
import com.masters.socratesai.interaction.service.InteractionLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InteractionControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private InteractionLogService interactionLogService;

    @BeforeEach
    void setUp() {
        interactionLogService = mock(InteractionLogService.class);
        InteractionController controller = new InteractionController(interactionLogService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldUpdateInteractionResult() throws Exception {
        UUID interactionId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        InteractionResultResponse response = InteractionResultResponse.builder()
                .interactionId(interactionId)
                .resolvedAfterFeedback(true)
                .fixedAfterMs(1500)
                .feedbackHelpful(true)
                .feedbackRating(5)
                .repeatedSameErrorAfterFeedback(false)
                .message("Interaction result updated successfully")
                .build();

        when(interactionLogService.updateInteractionResult(
                eq(interactionId),
                eq(true),
                eq(1500),
                eq(true),
                eq(5),
                eq("Useful hint"),
                eq(false)
        )).thenReturn(response);

        InteractionResultUpdateRequest request = new InteractionResultUpdateRequest();
        request.setResolvedAfterFeedback(true);
        request.setFixedAfterMs(1500);
        request.setFeedbackHelpful(true);
        request.setFeedbackRating(5);
        request.setStudentComment("Useful hint");
        request.setRepeatedSameErrorAfterFeedback(false);

        mockMvc.perform(post("/api/interactions/{interactionId}/result", interactionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interactionId").value(interactionId.toString()))
                .andExpect(jsonPath("$.resolvedAfterFeedback").value(true))
                .andExpect(jsonPath("$.fixedAfterMs").value(1500))
                .andExpect(jsonPath("$.feedbackHelpful").value(true))
                .andExpect(jsonPath("$.feedbackRating").value(5))
                .andExpect(jsonPath("$.repeatedSameErrorAfterFeedback").value(false));
    }

    @Test
    void shouldExportPolicyDatasetAsCsvAttachment() throws Exception {
        byte[] csv = "interaction_id,student_id\n1,2\n".getBytes(StandardCharsets.UTF_8);
        when(interactionLogService.exportPolicyDataset(true)).thenReturn(csv);

        mockMvc.perform(get("/api/interactions/policy-dataset").param("resolvedOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"policy_dataset_successful.csv\""))
                .andExpect(content().contentTypeCompatibleWith(new MediaType("text", "csv", StandardCharsets.UTF_8)))
                .andExpect(content().bytes(csv));
    }
}
