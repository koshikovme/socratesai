package com.masters.socratesai.user.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {
    private String fullName;
    private String university;
    private String groupName;
}