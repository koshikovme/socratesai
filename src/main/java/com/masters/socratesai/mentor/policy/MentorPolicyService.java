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
        PolicyFeatures features = PolicyFeatures.from(analyzer, context, attemptNo);
        PolicySelector selector = resolveSelector();

        FeedbackAction rawAction;
        String policyVersion;

        try {
            rawAction = selector.decide(features);
            policyVersion = selector.policyVersion();
        } catch (RuntimeException ex) {
            if (properties.getMode() != PolicyMode.ML || !properties.getMl().isFallbackToRule()) {
                throw ex;
            }

            log.warn("ML policy failed, falling back to rule selector: {}", ex.getMessage());
            rawAction = ruleBasedPolicyEngine.decide(features);
            policyVersion = ruleBasedPolicyEngine.policyVersion();
        }

        FeedbackAction guardedAction = policyGuardrailService.applyGuards(rawAction, features);
        return new PolicyDecision(guardedAction, policyVersion);
    }

    private PolicySelector resolveSelector() {
        if (properties.getMode() == PolicyMode.ML) {
            return mlPolicySelector;
        }
        return ruleBasedPolicyEngine;
    }
}
