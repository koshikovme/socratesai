package com.masters.socratesai.mentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.mentor.dto.MentorAnalyzeRequest;
import com.masters.socratesai.mentor.service.MentorService;
import com.masters.socratesai.mentor.service.MentorWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MentorControllerValidationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        MentorController controller = new MentorController(
                org.mockito.Mockito.mock(MentorService.class),
                org.mockito.Mockito.mock(MentorWorkflowService.class)
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldRejectAnalyzeFeedbackWhenCodeIsBlank() throws Exception {
        MentorAnalyzeRequest request = new MentorAnalyzeRequest();
        request.setStudentId(1L);
        request.setTaskId(2L);
        request.setLanguage("java");
        request.setCode(" ");
        request.setAttemptNo(1);

        mockMvc.perform(post("/api/mentor/analyze-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
