package com.campusfit.modules.activity.vo;

public record ActivityItemVO(
    String id,
    String title,
    String badge,
    String theme,
    String summary,
    String period,
    String reward,
    String participation,
    String scene,
    String status,
    int heat,
    int entries,
    boolean joined,
    String statusCopy,
    String progressText
) {
}
