package com.masters.socratesai.config;

import com.masters.socratesai.mentor.feedback.gemini.GeminiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class GeminiClientConfig {

    @Bean
    public RestClient geminiRestClient(GeminiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}