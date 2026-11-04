package com.masters.socratesai.mentor.feedback.gemini;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.model.FeedbackAction;
import com.masters.socratesai.mentor.feedback.port.PhrasingQuotaPort;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.mockito.Mockito.*;

class GeminiFeedbackServiceTest {

    private GeminiProperties properties;
    private MockRestServiceServer server;
    private GeminiFeedbackService service;
    private PhrasingQuotaPort quota;

    @BeforeEach
    void setUp() {
        properties = new GeminiProperties();
        properties.setEnabled(true);
        properties.setApiKey("gemini-key");
        properties.setModel("gemini-3.5-flash-lite");
        properties.setBaseUrl("https://gemini.example.test");

        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        quota = mock(PhrasingQuotaPort.class);
        when(quota.acquire(any())).thenReturn(true);
        service = new GeminiFeedbackService(properties, builder.build(), quota);
    }

    @Test
    void shouldReturnTrimmedFeedbackFromGeminiResponse() {
        expectTokens(80);
        server.expect(requestTo("https://gemini.example.test/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "gemini-key"))
                .andExpect(jsonPath("$.generationConfig.maxOutputTokens").value(128))
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
        expectTokens(80);
        server.expect(requestTo("https://gemini.example.test/models/gemini-3.5-flash-lite:generateContent"))
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

    @Test
    void exhaustedAllowanceNeverContactsProvider() {
        when(quota.acquire(42L)).thenReturn(false);
        assertThatThrownBy(() -> service.generateForStudent(42L, FeedbackAction.CONCEPTUAL_HINT,
                analyzerResult(), "private buffer", "public statement"))
                .hasMessage("Phrasing allowance unavailable");
        verify(quota).acquire(42L);
        server.verify();
    }

    @Test
    void oversizedOrUnknownInputNeverGeneratesPaidOutput() {
        expectTokens(2049);
        assertThatThrownBy(() -> service.generateForStudent(42L, FeedbackAction.CONCEPTUAL_HINT,
                analyzerResult(), "private buffer", "public statement"))
                .hasMessage("Phrasing context limit exceeded");
        server.verify();
    }

    @Test
    void providerFailureDoesNotExposeBodyOrAuthorization() {
        expectTokens(80);
        server.expect(requestTo("https://gemini.example.test/models/gemini-3.5-flash-lite:generateContent"))
                .andRespond(withServerError().body("private buffer gemini-key"));
        assertThatThrownBy(() -> service.generateForStudent(42L, FeedbackAction.CONCEPTUAL_HINT,
                analyzerResult(), "private buffer", "public statement"))
                .hasMessage("Phrasing unavailable").hasNoCause();
        server.verify();
    }

    @Test
    void enablingUnreviewedPricingOrExcessiveAllowanceFailsClosed() {
        assertThat(properties.isPricingValid()).isTrue();
        properties.getBudget().setMonthlyRequests(4000);
        assertThat(properties.isPricingValid()).isFalse();
        assertThat(service.isEnabled()).isFalse();
        properties.getBudget().setMonthlyRequests(3000);
        properties.setModel("another-model");
        assertThat(service.isEnabled()).isFalse();
    }

    private void expectTokens(int tokens) {
        server.expect(requestTo("https://gemini.example.test/models/gemini-3.5-flash-lite:countTokens"))
                .andExpect(header("x-goog-api-key", "gemini-key"))
                .andExpect(jsonPath("$.generateContentRequest.model").value("models/gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.generateContentRequest.generationConfig.maxOutputTokens").value(128))
                .andRespond(withSuccess("{\"totalTokens\":" + tokens + "}", MediaType.APPLICATION_JSON));
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
