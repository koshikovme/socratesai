package com.masters.socratesai.user.dto;

import lombok.Data;
import jakarta.validation.constraints.Pattern;

@Data
public class UpdateSettingsRequest {
    private Boolean darkMode;
    private Boolean emailNotifications;
    @Pattern(regexp = "en|ru|kk")
    private String preferredLanguage;
}
