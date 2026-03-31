package com.campusfit.modules.admin.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record AdminCooperationSaveRequest(
    @NotNull(message = "创作者不能为空")
    Long userId,

    @NotNull(message = "商家不能为空")
    Long merchantId,

    @NotBlank(message = "合作标题不能为空")
    @Size(max = 120, message = "合作标题最多 120 个字符")
    String cooperationTitle,

    @Size(max = 500, message = "合作说明最多 500 个字符")
    String cooperationDesc,

    @NotNull(message = "奖励金额不能为空")
    @DecimalMin(value = "0.01", message = "奖励金额必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "奖励金额格式不正确")
    BigDecimal rewardAmount,

    @Min(value = 1, message = "目标内容数必须大于 0")
    Integer targetPostCount,

    @Min(value = 0, message = "目标点赞数不能小于 0")
    Integer targetLikeCount,

    String deadlineAt
) {
}
