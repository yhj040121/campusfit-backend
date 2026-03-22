package com.campusfit.modules.announcement.vo;

public record AnnouncementVO(
    Long id,
    String badge,
    String title,
    String summary,
    String content,
    String publishTime,
    String expireTime
) {
}
