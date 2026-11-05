package com.masters.socratesai.mentor.feedback.port;

import com.masters.socratesai.mentor.feedback.NativeHint;
import com.masters.socratesai.mentor.nativecontext.MentorEvidence;
import com.masters.socratesai.mentor.policy.NativePolicyDecision;

/** Bounded AI generation from public context, separate from policy and execution infrastructure. */
public interface NativeHintPort {
    NativeHint generateNativeHint(Long studentId, String language, String code, String publicStatement,
                                  MentorEvidence evidence, NativePolicyDecision classification);
}
