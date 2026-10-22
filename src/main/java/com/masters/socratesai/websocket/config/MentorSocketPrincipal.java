package com.masters.socratesai.websocket.config;
import lombok.*;
import java.security.Principal;

/** No password hash or bearer token is retained in the WebSocket principal. */
@Value @ToString(onlyExplicitlyIncluded = true)
public class MentorSocketPrincipal implements Principal {
    @ToString.Include Long studentId;
    String name;
}
