package com.masters.socratesai.mentor.feedback.dto.gemini;
import lombok.*;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeminiGenerateContentRequest {
    private List<Content> contents;
    private Content systemInstruction;
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Content { private String role; private List<Part> parts; }
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Part { private String text; }
}
