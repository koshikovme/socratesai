package com.masters.socratesai.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.masters.socratesai.common.api.ApiErrorCode;
import com.masters.socratesai.common.api.ApiException;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.ToString;

@Data
public class UpdateProfileRequest {
    @NotBlank @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]*")
    private String fullName;
    @NotBlank @Email @Size(max = 120)
    private String email;
    @Size(max = 120) @Pattern(regexp = "[^\\p{Cntrl}]*")
    private String university;
    @Size(max = 80) @Pattern(regexp = "[^\\p{Cntrl}]*")
    private String groupName;
    @NotBlank @Pattern(regexp = "[a-f0-9]{64}")
    private String profileRevision;
    @Size(max = 100) @ToString.Exclude
    private String currentPassword;

    /** Role, account IDs and reporting classification are never self-editable. */
    @JsonAnySetter
    public void rejectUnknown(String field, Object value) { throw new ApiException(ApiErrorCode.INVALID_REQUEST); }
}
