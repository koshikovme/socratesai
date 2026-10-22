package com.masters.socratesai.mentor.nativecontext;

import com.masters.socratesai.mentor.dto.StudentContextDto;
import java.util.UUID;
import lombok.*;

@Value @Builder @ToString(onlyExplicitlyIncluded = true)
public class PreparedMentorContext {
    UUID sessionId;
    MentorEvidence evidence;
    StudentContextDto history;
    int formalAttempts;
    String sourceHash;
    String publicStatement;
}
