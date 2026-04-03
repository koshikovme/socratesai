package com.masters.socratesai.mentor.feedback.dto.openai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponseResponse {
    private String output_text;

    public String getOutput_text() {
        return output_text;
    }

    public void setOutput_text(String output_text) {
        this.output_text = output_text;
    }
}