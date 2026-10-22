package com.masters.socratesai.mentor.nativecontext;
import java.time.Instant;
import lombok.*;

/** Only server-authenticated JWT/session state can supply this identity. */
@Value @Builder @ToString(onlyExplicitlyIncluded = true)
public class MentorIdentity {
    @ToString.Include Long studentId;
    String username;
    Instant expiresAt;
}
