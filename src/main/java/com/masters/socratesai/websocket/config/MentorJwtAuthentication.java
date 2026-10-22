package com.masters.socratesai.websocket.config;

import com.masters.socratesai.common.api.*;
import com.masters.socratesai.mentor.nativecontext.MentorIdentity;
import com.masters.socratesai.security.*;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import java.time.Clock;

@Component @RequiredArgsConstructor
public class MentorJwtAuthentication {
    private final JwtService jwt;
    private final CustomUserDetailsService users;
    private final Clock clock;
    public MentorAuthentication authenticate(String header) {
        try {
            if (header == null || !header.startsWith("Bearer ") || header.length() > 4096) throw new IllegalStateException();
            String token = header.substring(7);
            var user = users.loadUserByUsername(jwt.extractUsername(token));
            if (!jwt.isTokenValid(token, user)) throw new IllegalStateException();
            var identity = MentorIdentity.builder().studentId(user.getId()).username(user.getUsername())
                    .expiresAt(jwt.extractClaim(token, Claims::getExpiration).toInstant()).build();
            return new MentorAuthentication(identity, new UsernamePasswordAuthenticationToken(new MentorSocketPrincipal(user.getId(), user.getUsername()), null, user.getAuthorities()));
        } catch (RuntimeException ex) { throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED); }
    }
    public MentorAuthentication refresh(MentorIdentity identity) {
        try {
            if (!identity.getExpiresAt().isAfter(clock.instant())) throw new IllegalStateException();
            var user = users.loadUserByUsername(identity.getUsername());
            if (!user.getId().equals(identity.getStudentId())) throw new IllegalStateException();
            return new MentorAuthentication(identity, new UsernamePasswordAuthenticationToken(new MentorSocketPrincipal(user.getId(), user.getUsername()), null, user.getAuthorities()));
        } catch (RuntimeException ex) { throw new ApiException(ApiErrorCode.AUTHENTICATION_REQUIRED); }
    }
}
