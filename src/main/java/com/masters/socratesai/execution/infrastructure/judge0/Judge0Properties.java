package com.masters.socratesai.execution.infrastructure.judge0;

import com.masters.socratesai.common.validation.ContesterLimits;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties("app.execution.judge0")
public class Judge0Properties {
    private boolean enabled;
    private String baseUrl = "";
    private String authHeader = "X-Auth-Token";
    private String apiKey = "";
    private boolean allowPlainHttp;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration requestTimeout = Duration.ofSeconds(5);
    private Duration operationTimeout = Duration.ofSeconds(30);
    private Duration initialBackoff = Duration.ofMillis(100);
    private Duration maxBackoff = Duration.ofSeconds(2);
    private Duration catalogTtl = Duration.ofMinutes(1);
    private int maxBatchSize = 5;
    private int maxConcurrentOperations = 16;
    private int maxResponseBytes = 2 * 1024 * 1024;
    private int maxSourceBytes = 80000;
    private int maxTestBytes = 65536;
    private int maxProcesses = 64;
    private int maxPollAttempts = 100;
    private List<RuntimeSpec> runtimes = List.of();

    /** No generated toString: configuration may contain credentials. */
    public void validate() {
        require(validDuration(connectTimeout) && validDuration(requestTimeout) && validDuration(operationTimeout)
                && validDuration(initialBackoff) && validDuration(maxBackoff) && validDuration(catalogTtl));
        require(initialBackoff.compareTo(maxBackoff) <= 0 && maxBackoff.compareTo(operationTimeout) <= 0
                && requestTimeout.compareTo(operationTimeout) <= 0);
        require(maxBatchSize >= 1 && maxBatchSize <= 20 && maxConcurrentOperations >= 1 && maxConcurrentOperations <= 64
                && maxResponseBytes >= 1024 && maxResponseBytes <= 8 * 1024 * 1024
                && maxSourceBytes >= 1 && maxSourceBytes <= 80000 && maxTestBytes >= 1 && maxTestBytes <= 262144
                && maxProcesses >= 1 && maxProcesses <= 128 && maxPollAttempts >= 1 && maxPollAttempts <= 1000);
        if (!enabled) return;
        require(System.getProperty("jdk.httpclient.HttpClient.log", "").isBlank());
        endpoint();
        require(authHeader != null && authHeader.matches("[A-Za-z0-9-]{1,80}")
                && !List.of("host", "content-length", "connection", "content-type", "accept").contains(authHeader.toLowerCase(java.util.Locale.ROOT)));
        require(apiKey != null && apiKey.matches("[\\x21-\\x7e]{1,512}"));
        require(runtimes != null && !runtimes.isEmpty() && runtimes.size() <= ContesterLimits.LANGUAGES);
        require(runtimes.stream().allMatch(spec -> spec != null && spec.key != null
                && spec.key.matches("[A-Za-z0-9._+\\-]{1,100}") && spec.languageKey != null
                && spec.languageKey.matches(ContesterLimits.LANGUAGE_KEY) && spec.languageId > 0
                && spec.version != null && !spec.version.isBlank() && spec.version.length() <= 100
                && spec.providerName != null && spec.providerName.length() <= 200 && spec.providerName.contains(spec.version)));
        require(runtimes.stream().map(RuntimeSpec::getKey).distinct().count() == runtimes.size());
        require(runtimes.stream().map(RuntimeSpec::getLanguageId).distinct().count() == runtimes.size());
        require(runtimes.stream().map(RuntimeSpec::getLanguageKey).distinct().count() == runtimes.size());
        require(runtimes.stream().allMatch(spec -> spec.entryPoint == null || spec.entryPoint.length() <= 120));
    }

    public URI endpoint() {
        URI uri;
        try {
            uri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("Invalid Judge0 endpoint configuration");
        }
        require(uri.getHost() != null && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null);
        boolean loopback = List.of("localhost", "127.0.0.1", "[::1]", "::1").contains(uri.getHost());
        require("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme()) && (loopback || allowPlainHttp)));
        return uri;
    }

    private boolean validDuration(Duration value) {
        return value != null && value.compareTo(Duration.ofMillis(1)) >= 0 && value.compareTo(Duration.ofMinutes(2)) <= 0;
    }

    private void require(boolean valid) {
        if (!valid) throw new IllegalArgumentException("Invalid Judge0 configuration");
    }

    @Getter
    @Setter
    public static class RuntimeSpec {
        private String key;
        private String languageKey;
        private String version;
        private int languageId;
        private String providerName;
        private String entryPoint;
    }
}
