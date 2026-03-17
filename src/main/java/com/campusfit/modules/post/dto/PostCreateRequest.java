package com.campusfit.modules.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
    @NotBlank(message = "Title is required")
    String title,
    @NotBlank(message = "Description is required")
    String desc,
    @Size(max = 9, message = "You can upload up to 9 images")
    List<String> imageUrls,
    @NotEmpty(message = "Please select at least one tag")
    List<String> tags,
    @NotBlank(message = "Product link is required")
    String productLink
) {
}