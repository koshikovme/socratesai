package com.masters.socratesai.mentor.feedback.gemini;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.feedback.dto.gemini.GeminiGenerateContentRequest;
import com.masters.socratesai.mentor.feedback.dto.gemini.GeminiGenerateContentResponse;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.masters.socratesai.mentor.feedback.port.PhrasingQuotaPort;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class GeminiFeedbackService {

    private final GeminiProperties properties;
    private final RestClient geminiRestClient;
    private final PhrasingQuotaPort quota;

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank()
                && properties.isPricingValid();
    }

    public String generateWithGemini(
            FeedbackAction action,
            AnalyzerResult analyzer,
            String code,
            String taskSummary
    ) {
        return generateForStudent(null, action, analyzer, code, taskSummary);
    }

    public String generateForStudent(Long studentId, FeedbackAction action, AnalyzerResult analyzer, String code, String taskSummary) {
        if (!isEnabled()) {
            throw new IllegalStateException("Gemini integration is disabled");
        }
        if (!quota.acquire(studentId)) throw new IllegalStateException("Phrasing allowance unavailable");

        String prompt = com.masters.socratesai.mentor.feedback.MentorPrompt.data(action, analyzer, code, taskSummary);
        var body = GeminiGenerateContentRequest.builder()
                .systemInstruction(new GeminiGenerateContentRequest.Content(null, List.of(new GeminiGenerateContentRequest.Part(
                        com.masters.socratesai.mentor.feedback.MentorPrompt.INSTRUCTIONS))))
                .contents(List.of(new GeminiGenerateContentRequest.Content("user", List.of(new GeminiGenerateContentRequest.Part(prompt)))))
                .generationConfig(new GeminiGenerateContentRequest.GenerationConfig(properties.getBudget().getMaxOutputTokens())).build();

        var tokenRequest = Map.of("generateContentRequest", Map.of("model", "models/" + properties.getModel(),
                "systemInstruction", body.getSystemInstruction(), "contents", body.getContents(), "generationConfig", body.getGenerationConfig()));
        final TokenCount count;
        try {
            count = geminiRestClient.post().uri("/models/{model}:countTokens", properties.getModel())
                    .header("x-goog-api-key", properties.getApiKey()).body(tokenRequest).retrieve().body(TokenCount.class);
        } catch (RuntimeException failure) { throw new IllegalStateException("Phrasing unavailable"); }
        if (count == null || count.totalTokens() == null || count.totalTokens() <= 0 || count.totalTokens() > properties.getBudget().getMaxInputTokens())
            throw new IllegalStateException("Phrasing context limit exceeded");

        final GeminiGenerateContentResponse response;
        try { response = geminiRestClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .build(properties.getModel()))
                .header("x-goog-api-key", properties.getApiKey())
                .body(body)
                .retrieve()
                .body(GeminiGenerateContentResponse.class);
        } catch (RuntimeException failure) { throw new IllegalStateException("Phrasing unavailable"); }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenCount(Integer totalTokens) {}
}
