package com.masters.socratesai.config;

import com.masters.socratesai.mentor.feedback.openai.OpenAiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenAiClientConfig {

    @Bean
    public RestClient openAiRestClient(OpenAiProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}