package com.masters.socratesai.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.net.URI;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/** Runs before bean creation, Flyway and database connections. Never echoes configuration values. */
public class ProductionConfigurationValidator implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (context.getEnvironment().acceptsProfiles(Profiles.of("prod"))) validate(context.getEnvironment());
    }

    void validate(Environment environment) {
        var secret = text(environment, "app.jwt.secret");
        try {
            byte[] bytes = Base64.getDecoder().decode(secret);
            require(bytes.length >= 64 && new String(bytes, java.nio.charset.StandardCharsets.UTF_8).toLowerCase(Locale.ROOT).indexOf("local-dev") < 0,
                    "app.jwt.secret");
            require(java.util.stream.IntStream.range(0, bytes.length).map(index -> bytes[index]).distinct().count() >= 16, "app.jwt.secret");
        } catch (IllegalArgumentException failure) { throw invalid("app.jwt.secret"); }
        long expiry = number(environment, "app.jwt.expiration-ms");
        require(expiry >= 60000 && expiry <= 86400000, "app.jwt.expiration-ms");
        var password = text(environment, "spring.datasource.password");
        require(password.length() >= 16 && !placeholder(password), "spring.datasource.password");
        text(environment, "spring.datasource.username");
        var jdbc = text(environment, "spring.datasource.url");
        require(jdbc.startsWith("jdbc:postgresql://"), "spring.datasource.url");
        var database = uri(jdbc.substring(5), "spring.datasource.url");
        require(database.getHost() != null && database.getUserInfo() == null && database.getFragment() == null
                && (database.getQuery() == null || !database.getQuery().matches("(?i).*(password|user)=.*")), "spring.datasource.url");
        var origin = endpoint(environment, "app.deployment.public-origin", true);
        require(origin.getPath().isEmpty() && origin.getQuery() == null && origin.getFragment() == null, "app.deployment.public-origin");
        var domain = text(environment, "app.deployment.domain");
        require(domain.matches("(?i)[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?") && !domain.contains("..")
                && domain.equalsIgnoreCase(origin.getHost()), "app.deployment.domain");
        require(text(environment, "app.websocket.allowed-origins").equals(origin.toString()), "app.websocket.allowed-origins");
        require(!bool(environment, "spring.jpa.show-sql") && !bool(environment, "spring.jpa.properties.hibernate.format_sql"), "spring.jpa.show-sql");
        List.of("org.hibernate.SQL", "org.hibernate.orm.jdbc.bind", "org.springframework.web", "org.springframework.messaging",
                        "org.springframework.web.socket", "org.springframework.web.client", "java.net.http")
                .forEach(logger -> require(!List.of("trace", "debug").contains(environment.getProperty("logging.level." + logger, "info").toLowerCase(Locale.ROOT)), "logging.level"));
        require(System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank(), "http-client-logging");
        require(List.of("rule", "ml").contains(text(environment, "app.policy.mode").toLowerCase(Locale.ROOT)), "app.policy.mode");
        try {
            double threshold = Double.parseDouble(text(environment, "app.policy.ml.min-confidence"));
            require(Double.isFinite(threshold) && threshold >= 0 && threshold <= 1, "app.policy.ml.min-confidence");
        } catch (IllegalArgumentException failure) { throw invalid("app.policy.ml.min-confidence"); }
        if (bool(environment, "app.policy.ml.enabled")) {
            endpoint(environment, "app.policy.ml.base-url", false);
            require("/predict-mentor-state".equals(text(environment, "app.policy.ml.predict-path")), "app.policy.ml.predict-path");
            text(environment, "app.policy.ml.version");
        }
        List.of("gemini", "openai").forEach(provider -> {
            if (bool(environment, "app." + provider + ".enabled")) {
                var key = text(environment, "app." + provider + ".api-key");
                require(!placeholder(key), "app." + provider + ".api-key");
                text(environment, "app." + provider + ".model");
                endpoint(environment, "app." + provider + ".base-url", true);
            }
        });
        require(!bool(environment, "app.execution.worker.enabled") || bool(environment, "app.execution.judge0.enabled"), "app.execution.worker.enabled");
        if (bool(environment, "app.execution.judge0.enabled")) {
            endpoint(environment, "app.execution.judge0.base-url", !bool(environment, "app.execution.judge0.allow-plain-http"));
            require(!placeholder(text(environment, "app.execution.judge0.api-key")), "app.execution.judge0.api-key");
            // Runtime/limit/duration validation is shared with Judge0Properties and the bounded worker properties.
        }
        if (bool(environment, "app.admin.bootstrap.enabled")) {
            require(text(environment, "app.admin.bootstrap.password").length() >= 16, "app.admin.bootstrap.password");
            text(environment, "app.admin.bootstrap.email");
            text(environment, "app.admin.bootstrap.full-name");
        }
    }

    private URI endpoint(Environment environment, String name, boolean https) {
        var value = uri(text(environment, name), name);
        require(value.getHost() != null && value.getUserInfo() == null && value.getQuery() == null && value.getFragment() == null
                && ("https".equals(value.getScheme()) || (!https && "http".equals(value.getScheme()))), name);
        return value;
    }
    private URI uri(String value, String name) {
        try { return URI.create(value); } catch (IllegalArgumentException failure) { throw invalid(name); }
    }
    private String text(Environment environment, String name) {
        String value;
        try { value = environment.getProperty(name); } catch (RuntimeException failure) { throw invalid(name); }
        require(value != null && !value.isBlank(), name);
        return value;
    }
    private boolean bool(Environment environment, String name) {
        String value = environment.getProperty(name, "false");
        require(List.of("true", "false").contains(value.toLowerCase(Locale.ROOT)), name);
        return Boolean.parseBoolean(value);
    }
    private long number(Environment environment, String name) {
        try { return Long.parseLong(text(environment, name)); } catch (IllegalArgumentException failure) { throw invalid(name); }
    }
    private boolean placeholder(String value) {
        return Arrays.stream(new String[]{"change-me", "replace", "example", "placeholder"})
                .anyMatch(value.toLowerCase(Locale.ROOT)::contains);
    }
    private void require(boolean condition, String name) { if (!condition) throw invalid(name); }
    private IllegalStateException invalid(String name) { return new IllegalStateException("Invalid production configuration: " + name); }
}
