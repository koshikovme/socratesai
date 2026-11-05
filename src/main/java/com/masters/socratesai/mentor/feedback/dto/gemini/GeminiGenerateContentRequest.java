package com.masters.socratesai.mentor.feedback.dto.gemini;
import lombok.*;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeminiGenerateContentRequest {
    private List<Content> contents;
    private Content systemInstruction;
    private GenerationConfig generationConfig;
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GenerationConfig {
        private int maxOutputTokens;
        private String responseMimeType;
        private Map<String, Object> responseJsonSchema;
    }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Content { private String role; private List<Part> parts; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Part { private String text; }
}
