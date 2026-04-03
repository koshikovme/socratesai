package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.Locale;

@Component
public class MlPolicySelector implements PolicySelector {

    private final MentorPolicyProperties properties;
    private final RestClient restClient;

    public MlPolicySelector(MentorPolicyProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder()
                .baseUrl(properties.getMl().getBaseUrl())
                .build();
    }

    @Override
    public FeedbackAction decide(PolicyFeatures features) {
        if (!properties.getMl().isEnabled()) {
            throw new IllegalStateException("ML policy mode is disabled");
        }

        PredictionResponse response = restClient.post()
                .uri(properties.getMl().getPredictPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(features)
                .retrieve()
                .body(PredictionResponse.class);

        if (response == null || !StringUtils.hasText(response.action())) {
            throw new IllegalStateException("ML policy service returned an empty action");
        }

        return FeedbackAction.valueOf(response.action().trim().toUpperCase(Locale.ROOT));
    }

    @Override
    public String policyVersion() {
        return properties.getMl().getVersion();
    }

    private record PredictionResponse(String action) {
    }
}
