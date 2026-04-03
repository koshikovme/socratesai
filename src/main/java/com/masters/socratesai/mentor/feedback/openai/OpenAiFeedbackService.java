package com.masters.socratesai.mentor.feedback.openai;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.feedback.dto.openai.OpenAiResponseRequest;
import com.masters.socratesai.mentor.feedback.dto.openai.OpenAiResponseResponse;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class OpenAiFeedbackService {

    private final OpenAiProperties properties;
    private final RestClient openAiRestClient;

    public OpenAiFeedbackService(OpenAiProperties properties, RestClient openAiRestClient) {
        this.properties = properties;
        this.openAiRestClient = openAiRestClient;
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getApiKey() != null
                && !properties.getApiKey().isBlank();
    }

    public String generateWithLlm(FeedbackAction action, AnalyzerResult analyzer, String taskSummary) {
        if (!isEnabled()) {
            throw new IllegalStateException("OpenAI integration is disabled");
        }

        String systemPrompt = """
                You are an educational programming mentor for CS1 students.
                Your job is to produce a SHORT feedback message.
                Rules:
                - Do not reveal the full solution.
                - Do not provide complete corrected code.
                - Keep the tone supportive and concise.
                - Follow the requested pedagogical action exactly.
                - Maximum 2 sentences.
                """;

        String userPrompt = """
                Generate feedback for a beginner programming student.

                Pedagogical action: %s
                Error type: %s
                Suspicious region: %s
                Compile success: %s
                Tests passed: %d
                Tests failed: %d
                Task summary: %s

                Return only the feedback text.
                """.formatted(
                action.name(),
                safe(analyzer.getErrorType()),
                safe(analyzer.getSuspiciousRegion()),
                analyzer.isCompileSuccess(),
                analyzer.getTestsPassed(),
                analyzer.getTestsFailed(),
                safe(taskSummary)
        );

        OpenAiResponseRequest body = new OpenAiResponseRequest(
                properties.getModel(),
                List.of(
                        new OpenAiResponseRequest.InputItem(
                                "system",
                                List.of(new OpenAiResponseRequest.ContentItem("input_text", systemPrompt))
                        ),
                        new OpenAiResponseRequest.InputItem(
                                "user",
                                List.of(new OpenAiResponseRequest.ContentItem("input_text", userPrompt))
                        )
                )
        );

        OpenAiResponseResponse response = openAiRestClient.post()
                .uri("/responses")
                .body(body)
                .retrieve()
                .body(OpenAiResponseResponse.class);

        if (response == null || response.getOutput_text() == null || response.getOutput_text().isBlank()) {
            throw new IllegalStateException("OpenAI returned empty feedback");
        }

        return response.getOutput_text().trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }
}