package com.campusfit.modules.post.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
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
    String productLink,
    @DecimalMin(value = "0.01", message = "商品价格必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "商品价格最多支持 8 位整数，并保留 2 位小数")
    BigDecimal productPrice,
    String activityId,
    String cooperationId
) {
}
