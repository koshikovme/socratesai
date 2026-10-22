package com.masters.socratesai.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.mock.env.MockEnvironment;

import java.util.Base64;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class ProductionConfigurationValidatorTest {
    private final ProductionConfigurationValidator validator = new ProductionConfigurationValidator();

    @Test void acceptsExplicitProductionSettingsAndRunsBeforeBeans() throws Exception {
        var environment = valid();
        environment.setActiveProfiles("prod");
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            validator.initialize(context);
            assertThat(context.isActive()).isFalse();
        }
        var registration = new Properties();
        try (var stream = getClass().getResourceAsStream("/META-INF/spring.factories")) { registration.load(stream); }
        assertThat(registration.getProperty("org.springframework.context.ApplicationContextInitializer"))
                .contains(ProductionConfigurationValidator.class.getName());
    }

    @Test void invalidCredentialsNeverAppearInExceptionOrCause() {
        var environment = valid().withProperty("app.jwt.secret", "PRIVATE_SECRET_MUST_NOT_APPEAR");
        assertThatThrownBy(() -> validator.validate(environment)).isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid production configuration: app.jwt.secret").hasNoCause();
        assertThatThrownBy(() -> validator.validate(valid().withProperty("spring.datasource.password", "PRIVATE_SHORT")))
                .hasMessage("Invalid production configuration: spring.datasource.password").hasNoCause();
    }

    @ParameterizedTest @CsvSource({
        "app.deployment.public-origin,http://practice.example.test",
        "app.deployment.public-origin,https://user:private@practice.example.test",
        "app.deployment.domain,other.example.test",
        "app.websocket.allowed-origins,https://*.example.test",
        "app.jwt.expiration-ms,0",
        "app.policy.ml.min-confidence,NaN",
        "app.policy.mode,unknown",
        "spring.jpa.show-sql,true",
        "logging.level.org.hibernate.orm.jdbc.bind,trace",
        "app.execution.worker.enabled,true"
    }) void rejectsUnsafeOrInconsistentSettings(String property, String value) {
        assertThatThrownBy(() -> validator.validate(valid().withProperty(property, value)))
                .isInstanceOf(IllegalStateException.class).hasNoCause();
    }

    @Test void enabledProvidersRequireRealConfigurationButDisabledProvidersDoNot() {
        validator.validate(valid());
        assertThatThrownBy(() -> validator.validate(valid().withProperty("app.openai.enabled", "true")))
                .hasMessage("Invalid production configuration: app.openai.api-key");
        assertThatThrownBy(() -> validator.validate(valid().withProperty("app.policy.ml.enabled", "true")
                .withProperty("app.policy.ml.base-url", "http://policy-api:8001")
                .withProperty("app.policy.ml.predict-path", "/predict")))
                .hasMessage("Invalid production configuration: app.policy.ml.predict-path");
    }

    @Test void productionCorsAndSockJsUseOnlyConfiguredHttpsOrigin() {
        var environment = valid().withProperty("app.websocket.allowed-origins", "https://practice.example.test");
        environment.setActiveProfiles("prod");
        assertThat(new FrontendOriginConfig(environment).allowedOriginPatterns()).containsExactly("https://practice.example.test");
        environment.setActiveProfiles("test");
        assertThat(new FrontendOriginConfig(environment).allowedOriginPatterns()).contains("http://localhost:*");
        try (var context = new GenericApplicationContext()) {
            context.setEnvironment(new MockEnvironment());
            validator.initialize(context); // Local development remains opt-in and compatible.
        }
    }

    private MockEnvironment valid() {
        byte[] bytes = new byte[64];
        IntStream.range(0, bytes.length).forEach(index -> bytes[index] = (byte) index);
        return new MockEnvironment().withProperty("app.jwt.secret", Base64.getEncoder().encodeToString(bytes))
                .withProperty("app.jwt.expiration-ms", "3600000")
                .withProperty("spring.datasource.url", "jdbc:postgresql://postgres:5432/socratesdb")
                .withProperty("spring.datasource.username", "socrates")
                .withProperty("spring.datasource.password", UUID.randomUUID().toString())
                .withProperty("app.deployment.domain", "practice.example.test")
                .withProperty("app.deployment.public-origin", "https://practice.example.test")
                .withProperty("app.websocket.allowed-origins", "https://practice.example.test")
                .withProperty("app.policy.mode", "ml").withProperty("app.policy.ml.min-confidence", "0.35");
    }
}
