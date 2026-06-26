package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MlPolicySelectorTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldSendJsonBodyToPolicyApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        AtomicReference<String> contentType = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));

            byte[] response = "{\"action\":\"CODE_HIGHLIGHT\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        MentorPolicyProperties properties = new MentorPolicyProperties();
        properties.getMl().setEnabled(true);
        properties.getMl().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getMl().setPredictPath("/predict");

        MlPolicySelector selector = new MlPolicySelector(properties);
        FeedbackAction action = selector.decide(new PolicyFeatures(
                "SYNTAX_ERROR",
                "HIGH",
                false,
                0,
                0,
                1,
                16,
                1,
                "NO_FEEDBACK",
                false,
                true,
                8,
                16
        ));

        assertThat(action).isEqualTo(FeedbackAction.CODE_HIGHLIGHT);
        assertThat(contentType.get()).contains("application/json");
        assertThat(requestBody.get()).contains("\"error_type\":\"SYNTAX_ERROR\"");
        assertThat(requestBody.get()).contains("\"compile_success\":false");
        assertThat(requestBody.get()).contains("\"passed_test_count\":0");
        assertThat(requestBody.get()).contains("\"total_feedback_count_in_session\":16");
        assertThat(requestBody.get()).doesNotContain("compileSuccess");
    }

    @Test
    void shouldBuildPredictUriWithOrWithoutSlashes() {
        MentorPolicyProperties properties = new MentorPolicyProperties();
        properties.getMl().setBaseUrl("http://localhost:8001/");
        properties.getMl().setPredictPath("predict");

        assertThat(new MlPolicySelector(properties).predictUri().toString())
                .isEqualTo("http://localhost:8001/predict");
    }

    @Test
    void shouldBuildPredictUriWhenEnvValuesContainQuotes() {
        MentorPolicyProperties properties = new MentorPolicyProperties();
        properties.getMl().setBaseUrl("\"http://localhost:8001\"");
        properties.getMl().setPredictPath("\"/predict-mentor-state\"");

        assertThat(new MlPolicySelector(properties).predictUri().toString())
                .isEqualTo("http://localhost:8001/predict-mentor-state");
    }

    @Test
    void shouldReadMentorStateMetadataFromPolicyApi() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>("");
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/predict-mentor-state", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] response = """
                    {"action":"GUIDING_QUESTION","mentor_state":"semantic_debug","confidence":0.61}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        MentorPolicyProperties properties = new MentorPolicyProperties();
        properties.getMl().setEnabled(true);
        properties.getMl().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getMl().setPredictPath("/predict-mentor-state");

        MlPolicySelector selector = new MlPolicySelector(properties);
        PolicyDecision decision = selector.decideWithMetadata(PolicyFeatures.from(
                analyzerResult(),
                studentContext(),
                2,
                "for (int i = 0; i < n; i++) { System.out.println(i); }"
        ));

        assertThat(decision.action()).isEqualTo(FeedbackAction.GUIDING_QUESTION);
        assertThat(decision.mentorState()).isEqualTo("semantic_debug");
        assertThat(decision.confidence()).isEqualTo(0.61);
        assertThat(requestBody.get()).contains("\"code_model_text\"");
        assertThat(requestBody.get()).contains("System.out.println");
    }

    private static com.masters.socratesai.analyzer.dto.AnalyzerResult analyzerResult() {
        com.masters.socratesai.analyzer.dto.AnalyzerResult result =
                new com.masters.socratesai.analyzer.dto.AnalyzerResult();
        result.setErrorType("WRONG_CONDITION");
        result.setSeverity("MEDIUM");
        result.setCompileSuccess(true);
        result.setTestsPassed(1);
        result.setTestsFailed(1);
        result.setCodeLines(1);
        return result;
    }

    private static com.masters.socratesai.mentor.dto.StudentContextDto studentContext() {
        com.masters.socratesai.mentor.dto.StudentContextDto context =
                new com.masters.socratesai.mentor.dto.StudentContextDto();
        context.setSameErrorCount(1);
        context.setTotalErrorsSeen(1);
        context.setTotalFeedbackCountInSession(0);
        return context;
    }
}
