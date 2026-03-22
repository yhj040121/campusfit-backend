package com.campusfit.modules.admin.vo;

public record AdminActivityItemVO(
    Long id,
    String activityCode,
    String title,
    String badge,
    String theme,
    String summary,
    String period,
    String reward,
    String participation,
    String scene,
    String statusText,
    String statusCode,
    boolean featured,
    int heat,
    int entries,
    int sortOrder,
    boolean active,
    String startTime,
    String endTime
) {
}
