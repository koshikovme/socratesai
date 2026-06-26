package com.masters.socratesai.mentor.policy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
public class MlPolicySelector implements PolicySelector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MentorPolicyProperties properties;
    private final HttpClient httpClient;

    public MlPolicySelector(MentorPolicyProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                // Uvicorn/FastAPI receives empty bodies when the JDK client attempts h2c upgrade.
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public FeedbackAction decide(PolicyFeatures features) {
        return decideWithMetadata(features).action();
    }

    public PolicyDecision decideWithMetadata(PolicyFeatures features) {
        if (!properties.getMl().isEnabled()) {
            throw new IllegalStateException("ML policy mode is disabled");
        }

        String payload = toJsonPayload(features);
        PredictionResponse response = requestPrediction(payload);

        if (response == null || !StringUtils.hasText(response.action())) {
            throw new IllegalStateException("ML policy service returned an empty action");
        }

        FeedbackAction action = FeedbackAction.valueOf(response.action().trim().toUpperCase(Locale.ROOT));
        return new PolicyDecision(
                action,
                policyVersion(),
                response.mentorState(),
                response.confidence()
        );
    }

    private PredictionResponse requestPrediction(String payload) {
        HttpRequest request = HttpRequest.newBuilder(predictUri())
                .version(HttpClient.Version.HTTP_1_1)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to call ML policy service: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while calling ML policy service", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException(
                    "ML policy service rejected request with status "
                            + response.statusCode()
                            + ": "
                            + response.body()
                            + "; payload="
                            + payload
            );
        }

        try {
            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            JsonNode confidenceNode = root.path("confidence");
            Double confidence = confidenceNode.isNumber() ? confidenceNode.asDouble() : null;
            return new PredictionResponse(
                    root.path("action").asText(null),
                    root.path("mentor_state").asText(null),
                    confidence
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("ML policy service returned invalid JSON: " + response.body(), e);
        }
    }

    @Override
    public String policyVersion() {
        return properties.getMl().getVersion();
    }

    static Map<String, Object> toPayload(PolicyFeatures features) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error_type", features.errorType());
        payload.put("severity", features.severity());
        payload.put("compile_success", features.compileSuccess());
        payload.put("tests_passed", features.testsPassed());
        payload.put("tests_failed", features.testsFailed());
        payload.put("passed_test_count", features.testsPassed());
        payload.put("same_error_count", features.sameErrorCount());
        payload.put("total_errors_seen", features.totalErrorsSeen());
        payload.put("attempt_no", features.attemptNo());
        payload.put("last_feedback_action", features.lastFeedbackAction());
        payload.put("last_feedback_success", features.lastFeedbackSuccess());
        payload.put("has_suspicious_region", features.hasSuspiciousRegion());
        payload.put("code_lines", features.codeLines());
        payload.put("total_feedback_count_in_session", features.totalFeedbackCountInSession());
        payload.put("code", features.code());
        payload.put("code_model_text", features.code());
        return payload;
    }

    static String toJsonPayload(PolicyFeatures features) {
        try {
            return OBJECT_MAPPER.writeValueAsString(toPayload(features));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ML policy request", e);
        }
    }

    URI predictUri() {
        String baseUrl = stripConfigQuotes(properties.getMl().getBaseUrl());
        String predictPath = stripConfigQuotes(properties.getMl().getPredictPath());
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("ML policy base URL is empty");
        }
        if (!StringUtils.hasText(predictPath)) {
            predictPath = "predict";
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        String normalizedPath = predictPath.startsWith("/") ? predictPath.substring(1) : predictPath;
        return URI.create(normalizedBase + normalizedPath);
    }

    private static String stripConfigQuotes(String value) {
        if (value == null) {
            return "";
        }

        String result = value.trim();
        while (result.length() >= 2
                && ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'")))) {
            result = result.substring(1, result.length() - 1).trim();
        }
        return result;
    }

    private record PredictionResponse(String action, String mentorState, Double confidence) {
    }
}
