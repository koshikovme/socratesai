package com.masters.socratesai.mentor.nativecontext;
import lombok.*;

@Value @ToString(onlyExplicitlyIncluded = true)
public class ComputedMentorFeedback {
    PreparedMentorContext prepared;
    NativeMentorFeedback feedback;
    int policyMs;
    int feedbackMs;
}
