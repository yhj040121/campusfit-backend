package com.campusfit.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAnnouncementSaveRequest(
    @NotBlank(message = "公告标题不能为空")
    @Size(max = 120, message = "公告标题最多 120 个字符")
    String title,

    @Size(max = 30, message = "公告标签最多 30 个字符")
    String badgeLabel,

    @NotBlank(message = "公告摘要不能为空")
    @Size(max = 255, message = "公告摘要最多 255 个字符")
    String summary,

    @Size(max = 5000, message = "公告正文过长")
    String content,

    Integer status,
    Integer pinnedFlag,
    Integer sortOrder,
    String publishTime,
    String expireTime
) {
}
