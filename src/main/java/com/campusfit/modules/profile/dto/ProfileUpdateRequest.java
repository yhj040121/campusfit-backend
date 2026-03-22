package com.campusfit.modules.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProfileUpdateRequest(
    @NotBlank(message = "Nickname is required")
    @Size(max = 20, message = "Nickname can contain up to 20 characters")
    String nickname,
    @Size(max = 255, message = "Avatar URL can contain up to 255 characters")
    String avatarUrl,
    @Size(max = 100, message = "School name can contain up to 100 characters")
    String schoolName,
    @Size(max = 50, message = "Grade can contain up to 50 characters")
    String gradeName,
    @Size(max = 255, message = "Signature can contain up to 255 characters")
    String signature
) {
}
