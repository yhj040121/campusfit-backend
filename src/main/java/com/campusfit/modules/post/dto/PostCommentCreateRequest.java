package com.campusfit.modules.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PostCommentCreateRequest(
    @NotBlank(message = "Comment content is required")
    @Size(max = 80, message = "Comment must be within 80 characters")
    String content,
    String replyToCommentId
) {
}
