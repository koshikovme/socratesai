package com.masters.socratesai.mentor.feedback.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.openai")
public class OpenAiProperties {

    private boolean enabled;
    private String apiKey;
    private String model;
    private String baseUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
}