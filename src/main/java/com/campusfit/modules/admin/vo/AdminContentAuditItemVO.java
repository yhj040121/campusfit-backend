package com.campusfit.modules.admin.vo;

public record AdminContentAuditItemVO(
    Long postId,
    String title,
    String author,
    String scene,
    String productStatus,
    String cooperationTitle,
    String auditStatus,
    int auditStatusCode,
    String createdAt
) {
}
