package com.masters.socratesai.mentor.policy;

import com.masters.socratesai.mentor.model.FeedbackAction;

public interface PolicySelector {

    FeedbackAction decide(PolicyFeatures features);

    String policyVersion();
}
