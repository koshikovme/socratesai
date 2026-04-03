package com.masters.socratesai.mentor.feedback.gemini;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.feedback.dto.gemini.GeminiGenerateContentRequest;
import com.masters.socratesai.mentor.feedback.dto.gemini.GeminiGenerateContentResponse;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class GeminiFeedbackService {

    private final GeminiProperties properties;
    private final RestClient geminiRestClient;

    public GeminiFeedbackService(GeminiProperties properties, RestClient geminiRestClient) {
        this.properties = properties;
        this.geminiRestClient = geminiRestClient;
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    public String generateWithGemini(
            FeedbackAction action,
            AnalyzerResult analyzer,
            String code,
            String taskSummary
    ) {
        if (!isEnabled()) {
            throw new IllegalStateException("Gemini integration is disabled");
        }

        String prompt = """
                You are an educational programming mentor for CS1 students.
                Produce a short feedback message.
                Rules:
                - Do not reveal the full solution.
                - Do not provide complete corrected code.
                - Keep it supportive and concise.
                - Maximum 2 sentences.

                Pedagogical action: %s
                Error type: %s
                Suspicious region: %s
                Compile success: %s
                Tests passed: %d
                Tests failed: %d
                Code: %s
                Task summary: %s

                Return only the feedback text.
                If a response is correct, then you can praise student/provide some recommendations about better solution
                """.formatted(
                action.name(),
                safe(analyzer.getErrorType()),
                safe(analyzer.getSuspiciousRegion()),
                analyzer.isCompileSuccess(),
                analyzer.getTestsPassed(),
                analyzer.getTestsFailed(),
                code,
                safe(taskSummary)
        );

        GeminiGenerateContentRequest body = new GeminiGenerateContentRequest(
                List.of(
                        new GeminiGenerateContentRequest.Content(
                                "user",
                                List.of(new GeminiGenerateContentRequest.Part(prompt))
                        )
                )
        );

        GeminiGenerateContentResponse response = geminiRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", properties.getApiKey())
                        .build(properties.getModel()))
                .body(body)
                .retrieve()
                .body(GeminiGenerateContentResponse.class);

        if (response == null
                || response.getCandidates() == null
                || response.getCandidates().isEmpty()
                || response.getCandidates().get(0).getContent() == null
                || response.getCandidates().get(0).getContent().getParts() == null
                || response.getCandidates().get(0).getContent().getParts().isEmpty()
                || response.getCandidates().get(0).getContent().getParts().get(0).getText() == null
                || response.getCandidates().get(0).getContent().getParts().get(0).getText().isBlank()) {
            throw new IllegalStateException("Gemini returned empty feedback");
        }

        return response.getCandidates().get(0).getContent().getParts().get(0).getText().trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}