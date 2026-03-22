package com.campusfit.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserSendCodeRequest(
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "Phone must be 11 digits")
    String phone,
    @Size(max = 20, message = "Scene is invalid")
    String scene
) {
}
