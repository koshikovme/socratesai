package com.masters.socratesai.interaction.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.interaction.dto.InteractionResultUpdateRequest;
import com.masters.socratesai.interaction.service.InteractionLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InteractionControllerValidationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new InteractionController(org.mockito.Mockito.mock(InteractionLogService.class)))
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldRejectNegativeResolutionLatency() throws Exception {
        InteractionResultUpdateRequest request = new InteractionResultUpdateRequest();
        request.setResolvedAfterFeedback(true);
        request.setFixedAfterMs(-10);

        mockMvc.perform(post("/api/interactions/{interactionId}/result", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectInvalidFeedbackRating() throws Exception {
        InteractionResultUpdateRequest request = new InteractionResultUpdateRequest();
        request.setResolvedAfterFeedback(true);
        request.setFeedbackRating(6);

        mockMvc.perform(post("/api/interactions/{interactionId}/result", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
