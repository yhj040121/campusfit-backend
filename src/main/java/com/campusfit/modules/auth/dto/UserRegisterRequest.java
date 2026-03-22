package com.campusfit.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegisterRequest(
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^1\\d{10}$", message = "Phone must be 11 digits")
    String phone,
    @NotBlank(message = "Verification code is required")
    String code,
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 20, message = "Password must be 6-20 characters")
    String password,
    @NotBlank(message = "Please confirm password")
    String confirmPassword,
    @NotBlank(message = "Nickname is required")
    @Size(max = 50, message = "Nickname must be within 50 characters")
    String nickname,
    @Size(max = 100, message = "School must be within 100 characters")
    String schoolName,
    @Size(max = 50, message = "Grade must be within 50 characters")
    String gradeName,
    @Size(max = 255, message = "Signature must be within 255 characters")
    String signature,
    @Size(max = 255, message = "Avatar URL must be within 255 characters")
    String avatarUrl
) {
}
