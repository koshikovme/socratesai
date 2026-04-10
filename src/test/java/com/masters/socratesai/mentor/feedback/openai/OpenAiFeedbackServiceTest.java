package com.masters.socratesai.mentor.feedback.openai;

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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenAiFeedbackServiceTest {

    private OpenAiProperties properties;
    private MockRestServiceServer server;
    private OpenAiFeedbackService service;

    @BeforeEach
    void setUp() {
        properties = new OpenAiProperties();
        properties.setEnabled(true);
        properties.setApiKey("openai-key");
        properties.setModel("gpt-4.1-mini");
        properties.setBaseUrl("https://openai.example.test");

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new OpenAiFeedbackService(properties, builder.build());
    }

    @Test
    void shouldReturnTrimmedFeedbackFromOpenAiResponse() {
        server.expect(requestTo("https://openai.example.test/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer openai-key"))
                .andRespond(withSuccess("""
                        {
                          "output_text": "  Check the stopping condition more carefully.  "
                        }
                        """, MediaType.APPLICATION_JSON));

        String feedback = service.generateWithLlm(
                FeedbackAction.CONCEPTUAL_HINT,
                analyzerResult(),
                "Count numbers"
        );

        assertThat(feedback).isEqualTo("Check the stopping condition more carefully.");
        server.verify();
    }

    @Test
    void shouldRejectEmptyOpenAiFeedback() {
        server.expect(requestTo("https://openai.example.test/responses"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"output_text\":\"   \"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.generateWithLlm(
                FeedbackAction.GUIDING_QUESTION,
                analyzerResult(),
                "Count numbers"
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OpenAI returned empty feedback");
    }

    @Test
    void shouldReportDisabledWhenApiKeyMissing() {
        properties.setApiKey(" ");

        assertThat(service.isEnabled()).isFalse();
    }

    private AnalyzerResult analyzerResult() {
        AnalyzerResult result = new AnalyzerResult();
        result.setErrorType("WRONG_CONDITION");
        result.setSeverity("MEDIUM");
        result.setCompileSuccess(true);
        result.setTestsPassed(0);
        result.setTestsFailed(1);
        result.setSuspiciousRegion("line 3");
        return result;
    }
}
