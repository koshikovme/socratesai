package com.masters.socratesai.user.service;

import com.masters.socratesai.user.dto.UserProfileResponse;
import com.masters.socratesai.user.model.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserProfileMapper {
    public static UserProfileResponse response(User user) {
        return UserProfileResponse.builder().id(user.getId()).email(user.getEmail()).fullName(user.getFullName())
                .role(user.getRole()).university(user.getUniversity()).groupName(user.getGroupName())
                .darkMode(user.getSettings().getDarkMode()).emailNotifications(user.getSettings().getEmailNotifications())
                .preferredLanguage(user.getSettings().getPreferredLanguage()).createdAt(user.getCreatedAt())
                .profileRevision(revision(user)).build();
    }

    /** Opaque compare-and-set value for editable fields; no extra schema or sensitive input. */
    public static String revision(User user) {
        String fields = Stream.of(user.getId().toString(), user.getFullName(), user.getEmail(), user.getUniversity(), user.getGroupName())
                .map(value -> value == null ? "-1:" : value.length() + ":" + value).collect(Collectors.joining());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(fields.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Required profile digest unavailable");
        }
    }
}
