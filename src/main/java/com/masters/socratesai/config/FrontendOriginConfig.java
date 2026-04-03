package com.masters.socratesai.config;

import org.springframework.stereotype.Component;

@Component
public class FrontendOriginConfig {

    private static final String[] DEV_ORIGIN_PATTERNS = {
            "http://localhost:*",
            "https://localhost:*",
            "http://127.0.0.1:*",
            "https://127.0.0.1:*"
    };

    public String[] allowedOriginPatterns() {
        return DEV_ORIGIN_PATTERNS.clone();
    }
}
