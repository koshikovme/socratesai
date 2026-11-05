package com.masters.socratesai.mentor.feedback;

import com.masters.socratesai.mentor.model.FeedbackAction;

/** Structured model output; never includes a provider body, source or test diagnostics. */
public record NativeHint(String hint, String mentorState, FeedbackAction action) {
    @Override public String toString() { return "NativeHint[redacted]"; }
}
