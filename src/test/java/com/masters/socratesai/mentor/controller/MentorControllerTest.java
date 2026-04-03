package com.masters.socratesai.mentor.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.mentor.dto.MentorAnalyzeRequest;
import com.masters.socratesai.mentor.dto.MentorRequest;
import com.masters.socratesai.mentor.dto.MentorResponse;
import com.masters.socratesai.mentor.service.MentorService;
import com.masters.socratesai.mentor.service.MentorWorkflowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MentorControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private MentorService mentorService;
    private MentorWorkflowService mentorWorkflowService;

    @BeforeEach
    void setUp() {
        mentorService = mock(MentorService.class);
        mentorWorkflowService = mock(MentorWorkflowService.class);
        MentorController controller = new MentorController(mentorService, mentorWorkflowService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldReturnFeedbackResponse() throws Exception {
        MentorResponse response = new MentorResponse();
        response.setInteractionId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        response.setSessionId(UUID.fromString("00000000-0000-0000-0000-000000000022"));
        response.setAction("CONCEPTUAL_HINT");
        response.setFeedbackText("Review the condition carefully.");
        response.setErrorType("WRONG_CONDITION");
        response.setSuspiciousRegion("line 4");

        when(mentorService.mentor(any(MentorRequest.class))).thenReturn(response);

        MentorRequest request = new MentorRequest();
        request.setStudentId(1L);
        request.setTaskId(2L);
        request.setAttemptNo(3);
        request.setCode("if (x > 0) {}");

        mockMvc.perform(post("/api/mentor/feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CONCEPTUAL_HINT"))
                .andExpect(jsonPath("$.feedbackText").value("Review the condition carefully."))
                .andExpect(jsonPath("$.errorType").value("WRONG_CONDITION"));
    }

    @Test
    void shouldReturnAnalyzeFeedbackResponse() throws Exception {
        MentorResponse response = new MentorResponse();
        response.setAction("CODE_HIGHLIGHT");
        response.setFeedbackText("Check line 2.");
        response.setErrorType("SYNTAX_ERROR");
        response.setSuspiciousRegion("line 2");

        when(mentorWorkflowService.analyzeAndMentor(any(MentorAnalyzeRequest.class))).thenReturn(response);

        MentorAnalyzeRequest request = new MentorAnalyzeRequest();
        request.setStudentId(1L);
        request.setTaskId(2L);
        request.setLanguage("java");
        request.setCode("if (");
        request.setAttemptNo(1);

        mockMvc.perform(post("/api/mentor/analyze-feedback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("CODE_HIGHLIGHT"))
                .andExpect(jsonPath("$.suspiciousRegion").value("line 2"));
    }
}
