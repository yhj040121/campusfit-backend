package com.campusfit.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserLoginRequest(
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "Phone must be 11 digits")
    String phone,
    @Size(max = 20, message = "Login type is invalid")
    String loginType,
    @Size(min = 6, max = 20, message = "Password must be 6-20 characters")
    String password,
    @Size(max = 6, message = "Verification code is invalid")
    String code
) {
}
