package com.masters.socratesai.mentor.feedback.gemini;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@ConfigurationProperties(prefix = "app.gemini")
@Getter @Setter @Validated
public class GeminiProperties {

    private boolean enabled;
    private String apiKey;
    private String model;
    private String baseUrl;
    @Valid @NotNull private Budget budget = new Budget();

    @AssertTrue(message = "Gemini allowance does not match the reviewed model pricing")
    public boolean isPricingValid() {
        if (!enabled) return true;
        if (budget == null || model == null || !model.equals(budget.getPricedModel())
                || budget.getInputUsdPerMillion() == null || budget.getOutputUsdPerMillion() == null || budget.getMonthlyUsd() == null) return false;
        var maximum = budget.getInputUsdPerMillion().multiply(BigDecimal.valueOf(budget.getMaxInputTokens()))
                .add(budget.getOutputUsdPerMillion().multiply(BigDecimal.valueOf(budget.getMaxOutputTokens())))
                .multiply(BigDecimal.valueOf(budget.getMonthlyRequests())).movePointLeft(6);
        return maximum.compareTo(budget.getMonthlyUsd()) <= 0;
    }

    @Getter @Setter
    public static class Budget {
        @NotBlank private String pricedModel = "gemini-3.5-flash-lite";
        @Min(128) @Max(8192) private int maxInputTokens = 2048;
        @Min(32) @Max(512) private int maxOutputTokens = 128;
        @Min(1) @Max(1000000) private int monthlyRequests = 3000;
        @Min(1) @Max(120) private int requestsPerMinute = 60;
        @Min(1) @Max(100) private int dailyRequestsPerStudent = 30;
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") private BigDecimal monthlyUsd = new BigDecimal("3.00");
        @NotNull @DecimalMin("0.01") @DecimalMax("100.00") private BigDecimal inputUsdPerMillion = new BigDecimal("0.30");
        @NotNull @DecimalMin("0.01") @DecimalMax("1000.00") private BigDecimal outputUsdPerMillion = new BigDecimal("2.50");
    }
}
