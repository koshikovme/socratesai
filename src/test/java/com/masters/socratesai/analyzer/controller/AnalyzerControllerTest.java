package com.masters.socratesai.analyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.analyzer.dto.AnalyzerRequest;
import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.analyzer.service.AnalyzerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalyzerControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private AnalyzerService analyzerService;

    @BeforeEach
    void setUp() {
        analyzerService = mock(AnalyzerService.class);
        AnalyzerController controller = new AnalyzerController(analyzerService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void shouldReturnAnalyzerResult() throws Exception {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType("OFF_BY_ONE");
        result.setSeverity("MEDIUM");
        result.setCompileSuccess(true);
        result.setTestsPassed(1);
        result.setTestsFailed(2);
        result.setSuspiciousRegion("for loop condition");

        when(analyzerService.analyze(any(AnalyzerRequest.class))).thenReturn(result);

        AnalyzerRequest request = new AnalyzerRequest();
        request.setStudentId(1L);
        request.setTaskId(2L);
        request.setLanguage("java");
        request.setCode("for (int i = 0; i <= n; i++) {}");
        request.setAttemptNo(1);

        mockMvc.perform(post("/api/analyzer/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.errorType").value("OFF_BY_ONE"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.compileSuccess").value(true))
                .andExpect(jsonPath("$.testsPassed").value(1))
                .andExpect(jsonPath("$.testsFailed").value(2));
    }
}
