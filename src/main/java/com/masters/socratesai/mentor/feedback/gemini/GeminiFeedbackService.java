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

        String prompt = com.masters.socratesai.mentor.feedback.MentorPrompt.data(action, analyzer, code, taskSummary);
        var body = GeminiGenerateContentRequest.builder()
                .systemInstruction(new GeminiGenerateContentRequest.Content(null, List.of(new GeminiGenerateContentRequest.Part(
                        com.masters.socratesai.mentor.feedback.MentorPrompt.INSTRUCTIONS))))
                .contents(List.of(new GeminiGenerateContentRequest.Content("user", List.of(new GeminiGenerateContentRequest.Part(prompt))))).build();

        GeminiGenerateContentResponse response = geminiRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .build(properties.getModel()))
                .header("x-goog-api-key", properties.getApiKey())
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