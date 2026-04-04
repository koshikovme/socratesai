package com.masters.socratesai.mentor.feedback.gemini;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeminiFeedbackServiceTest {

    private GeminiProperties properties;
    private MockRestServiceServer server;
    private GeminiFeedbackService service;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("gemini-key");
        properties.setModel("gemini-2.5-flash");
        properties.setBaseUrl("https://gemini.example.test");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        service = new GeminiFeedbackService(properties, builder.build());
    }

    @Test
    void shouldReturnTrimmedFeedbackFromGeminiResponse() {
        server.expect(requestTo("https://gemini.example.test/models/gemini-2.5-flash:generateContent?key=gemini-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        {
                          "candidates": [
                            {
                              "content": {
                                "parts": [
                                  { "text": "  Focus on the loop boundary.  " }
                                ]
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        String feedback = service.generateWithGemini(
                FeedbackAction.CONCEPTUAL_HINT,
                analyzerResult(),
                "for (int i = 0; i <= n; i++) {}",
                "Count numbers"
        );

        assertThat(feedback).isEqualTo("Focus on the loop boundary.");
        server.verify();
    }

    @Test
    void shouldRejectEmptyGeminiFeedback() {
        server.expect(requestTo("https://gemini.example.test/models/gemini-2.5-flash:generateContent?key=gemini-key"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateWithGemini(
                FeedbackAction.CODE_HIGHLIGHT,
                analyzerResult(),
                "if (",
                "Validate brackets"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Gemini returned empty feedback");
    }

    @Test
    void shouldReportDisabledWhenApiKeyMissing() {
        properties.setApiKey(" ");
        assertThat(service.isEnabled()).isFalse();
    }

    private AnalyzerResult analyzerResult() {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType("OFF_BY_ONE");
        result.setSeverity("MEDIUM");
        result.setCompileSuccess(true);
        result.setTestsPassed(0);
        result.setTestsFailed(1);
        result.setSuspiciousRegion("line 3");
        return result;
    }
}
