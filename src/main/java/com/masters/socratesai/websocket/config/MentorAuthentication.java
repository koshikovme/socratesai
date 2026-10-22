package com.masters.socratesai.websocket.config;

import com.masters.socratesai.mentor.nativecontext.MentorIdentity;
import org.springframework.security.core.Authentication;
import lombok.*;

@Value @ToString(onlyExplicitlyIncluded = true)
public class MentorAuthentication {
    MentorIdentity identity;
    Authentication principal;
}
