package com.masters.socratesai.mentor.feedback.dto.gemini;

import java.util.List;

public class GeminiGenerateContentRequest {
    private List<Content> contents;

    public GeminiGenerateContentRequest() {}

    public GeminiGenerateContentRequest(List<Content> contents) {
        this.contents = contents;
    }

    public List<Content> getContents() { return contents; }
    public void setContents(List<Content> contents) { this.contents = contents; }

    public static class Content {
        private String role;
        private List<Part> parts;

        public Content() {}

        public Content(String role, List<Part> parts) {
            this.role = role;
            this.parts = parts;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public List<Part> getParts() { return parts; }
        public void setParts(List<Part> parts) { this.parts = parts; }
    }

    public static class Part {
        private String text;

        public Part() {}

        public Part(String text) {
            this.text = text;
        }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}