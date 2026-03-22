package com.campusfit.modules.admin.vo;

public record AdminAnnouncementItemVO(
    Long id,
    String title,
    String badge,
    String summary,
    String content,
    String publishTime,
    String expireTime,
    String statusText,
    int statusCode,
    boolean pinned,
    int sortOrder
) {
}
