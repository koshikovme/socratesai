package com.masters.socratesai.config;

import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class FrontendOriginConfig {
    private final Environment environment;

    private static final String[] DEV_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "https://localhost:*",
            "http://127.0.0.1:*",
            "https://127.0.0.1:*"
    };

    public String[] allowedOriginPatterns() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            return environment.getRequiredProperty("app.websocket.allowed-origins").split(",");
        }
        return DEV_ORIGIN_PATTERNS.clone();
    }
}
