package com.campusfit.modules.admin.vo;

import java.math.BigDecimal;

public record AdminCooperationItemVO(
    Long id,
    String cooperationCode,
    String displayCode,
    String title,
    String desc,
    String creatorName,
    String merchantName,
    String status,
    int statusCode,
    BigDecimal rewardAmount,
    int targetPostCount,
    int approvedPostCount,
    int submittedPostCount,
    int targetLikeCount,
    int approvedLikeCount,
    String deadlineAt,
    String acceptedAt,
    String rewardIssuedAt,
    boolean rewardReady,
    boolean rewardIssued
) {
}
