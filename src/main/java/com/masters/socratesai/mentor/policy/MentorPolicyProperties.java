package com.masters.socratesai.mentor.policy;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.policy")
public class MentorPolicyProperties {

    private PolicyMode mode = PolicyMode.RULE;
    private String ruleVersion = "rule-v1";
    private String guardrailVersion = "guardrails-v1";
    private final Ml ml = new Ml();

    public PolicyMode getMode() {
        return mode;
    }

    public void setMode(PolicyMode mode) {
        this.mode = mode;
    }

    public String getRuleVersion() {
        return ruleVersion;
    }

    public void setRuleVersion(String ruleVersion) {
        this.ruleVersion = ruleVersion;
    }

    public String getGuardrailVersion() {
        return guardrailVersion;
    }

    public void setGuardrailVersion(String guardrailVersion) {
        this.guardrailVersion = guardrailVersion;
    }

    public Ml getMl() {
        return ml;
    }

    public static class Ml {
        private boolean enabled = false;
        private String version = "ml-v1";
        private String baseUrl = "http://localhost:8001";
        private String predictPath = "/predict";
        private boolean fallbackToRule = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getPredictPath() {
            return predictPath;
        }

        public void setPredictPath(String predictPath) {
            this.predictPath = predictPath;
        }

        public boolean isFallbackToRule() {
            return fallbackToRule;
        }

        public void setFallbackToRule(boolean fallbackToRule) {
            this.fallbackToRule = fallbackToRule;
        }
    }
}
