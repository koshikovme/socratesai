package com.masters.socratesai.user.dto;

import lombok.Data;

@Data
public class UpdateSettingsRequest {
    private Boolean darkMode;
    private Boolean emailNotifications;
    private String preferredLanguage;
}