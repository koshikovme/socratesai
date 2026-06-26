package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.analyzer.dto.AnalyzerResult;
import com.masters.socratesai.mentor.dto.StudentContextDto;
import com.masters.socratesai.mentor.model.FeedbackAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class MentorPolicyService {

    private static final Logger log = LoggerFactory.getLogger(MentorPolicyService.class);

    private final RuleBasedPolicyEngine ruleBasedPolicyEngine;
    private final MlPolicySelector mlPolicySelector;
    private final PolicyGuardrailService policyGuardrailService;
    private final MentorPolicyProperties properties;

    public MentorPolicyService(
            RuleBasedPolicyEngine ruleBasedPolicyEngine,
            MlPolicySelector mlPolicySelector,
            PolicyGuardrailService policyGuardrailService,
            MentorPolicyProperties properties
    ) {
        this.ruleBasedPolicyEngine = ruleBasedPolicyEngine;
        this.mlPolicySelector = mlPolicySelector;
        this.policyGuardrailService = policyGuardrailService;
        this.properties = properties;
    }

    public PolicyDecision decide(AnalyzerResult analyzer, StudentContextDto context, Integer attemptNo) {
        return decide(analyzer, context, attemptNo, null);
    }

    public PolicyDecision decide(AnalyzerResult analyzer, StudentContextDto context, Integer attemptNo, String code) {
        PolicyFeatures features = PolicyFeatures.from(analyzer, context, attemptNo, code);
        if (properties.getMode() == PolicyMode.NO_POLICY) {
            return new PolicyDecision(FeedbackAction.CONCEPTUAL_HINT, "no-policy-v1");
        }

        PolicyDecision rawDecision;

        try {
            rawDecision = decideRaw(features);
        } catch (RuntimeException ex) {
            if (properties.getMode() != PolicyMode.ML || !properties.getMl().isFallbackToRule()) {
                throw ex;
            }

            log.warn("ML policy failed, falling back to rule selector: {}", ex.getMessage());
            rawDecision = new PolicyDecision(
                    ruleBasedPolicyEngine.decide(features),
                    ruleBasedPolicyEngine.policyVersion()
            );
        }

        if (shouldUseRuleFallbackForLowConfidence(rawDecision)) {
            FeedbackAction fallbackAction = ruleBasedPolicyEngine.decide(features);
            rawDecision = new PolicyDecision(
                    fallbackAction,
                    lowConfidencePolicyVersion(rawDecision.policyVersion()),
                    rawDecision.mentorState(),
                    rawDecision.confidence()
            );
        }

        FeedbackAction guardedAction = policyGuardrailService.applyGuards(rawDecision.action(), features);
        return new PolicyDecision(
                guardedAction,
                rawDecision.policyVersion(),
                rawDecision.mentorState(),
                rawDecision.confidence()
        );
    }

    private PolicyDecision decideRaw(PolicyFeatures features) {
        if (properties.getMode() == PolicyMode.ML) {
            return mlPolicySelector.decideWithMetadata(features);
        }
        return new PolicyDecision(
                ruleBasedPolicyEngine.decide(features),
                ruleBasedPolicyEngine.policyVersion()
        );
    }

    private boolean shouldUseRuleFallbackForLowConfidence(PolicyDecision decision) {
        if (properties.getMode() != PolicyMode.ML || !properties.getMl().isFallbackToRule()) {
            return false;
        }

        double minConfidence = properties.getMl().getMinConfidence();
        if (minConfidence <= 0.0d) {
            return false;
        }

        if ((decision.mentorState() == null || decision.mentorState().isBlank()) && decision.confidence() == null) {
            return false;
        }

        Double confidence = decision.confidence();
        return confidence == null || confidence < minConfidence;
    }

    private String lowConfidencePolicyVersion(String mlPolicyVersion) {
        String version = mlPolicyVersion == null || mlPolicyVersion.isBlank()
                ? properties.getMl().getVersion()
                : mlPolicyVersion;
        return version + "+rule-low-confidence";
    }
}
