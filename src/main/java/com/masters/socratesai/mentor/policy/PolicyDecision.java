package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;

public record PolicyDecision(
        FeedbackAction action,
        String policyVersion
) {
}
