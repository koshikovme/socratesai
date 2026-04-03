package com.masters.socratesai.user.model;

import jakarta.persistence.Embeddable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Embeddable
public class UserSettings {
    @Builder.Default
    private Boolean darkMode = false;

    @Builder.Default
    private Boolean emailNotifications = true;

    @Builder.Default
    private String preferredLanguage = "en";
}