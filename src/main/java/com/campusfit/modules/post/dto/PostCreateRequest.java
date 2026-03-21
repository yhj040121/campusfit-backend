package com.campusfit.modules.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PostCreateRequest(
    @NotBlank(message = "标题不能为空")
    String title,
    @NotBlank(message = "描述不能为空")
    String desc,
    @NotEmpty(message = "请至少上传 1 张图片")
    @Size(max = 9, message = "图片最多 9 张")
    List<String> imageUrls,
    @NotEmpty(message = "请至少选择一个标签")
    List<String> tags,
    @NotBlank(message = "商品链接不能为空")
    String productLink,
    String activityId
) {
}
