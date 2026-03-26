package com.campusfit.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminActivitySaveRequest(
    @NotBlank(message = "活动标题不能为空")
    @Size(max = 120, message = "活动标题最多 120 个字符")
    String title,

    @Size(max = 50, message = "活动标签最多 50 个字符")
    String badgeLabel,

    @NotBlank(message = "活动主题说明不能为空")
    @Size(max = 255, message = "活动主题说明最多 255 个字符")
    String themeDesc,

    @NotBlank(message = "活动摘要不能为空")
    @Size(max = 255, message = "活动摘要最多 255 个字符")
    String summaryDesc,

    @NotBlank(message = "活动时间文案不能为空")
    @Size(max = 50, message = "活动时间文案最多 50 个字符")
    String periodText,

    @NotBlank(message = "奖励说明不能为空")
    @Size(max = 255, message = "奖励说明最多 255 个字符")
    String rewardDesc,

    @NotBlank(message = "参与说明不能为空")
    @Size(max = 255, message = "参与说明最多 255 个字符")
    String participationDesc,

    @NotBlank(message = "活动场景不能为空")
    @Size(max = 50, message = "活动场景最多 50 个字符")
    String sceneLabel,

    @Size(max = 20, message = "活动状态码过长")
    String statusCode,

    Integer featuredFlag,
    Integer publishSelectableFlag,
    Integer heatValue,
    Integer sortOrder,
    Integer status,
    String startTime,
    String endTime
) {
}
