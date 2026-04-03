package com.masters.socratesai.mentor.feedback.dto.openai;

import java.util.List;

public class OpenAiResponseRequest {
    private String model;
    private List<InputItem> input;

    public OpenAiResponseRequest() {}

    public OpenAiResponseRequest(String model, List<InputItem> input) {
        this.model = model;
        this.input = input;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public List<InputItem> getInput() { return input; }
    public void setInput(List<InputItem> input) { this.input = input; }

    public static class InputItem {
        private String role;
        private List<ContentItem> content;

        public InputItem() {}

        public InputItem(String role, List<ContentItem> content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }

        public List<ContentItem> getContent() { return content; }
        public void setContent(List<ContentItem> content) { this.content = content; }
    }

    public static class ContentItem {
        private String type;
        private String text;

        public ContentItem() {}

        public ContentItem(String type, String text) {
            this.type = type;
            this.text = text;
        }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}