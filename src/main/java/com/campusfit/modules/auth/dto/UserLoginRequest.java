package com.campusfit.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserLoginRequest(
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "Phone must be 11 digits")
    String phone,
    @NotBlank(message = "Verification code is required")
    String code
) {
}