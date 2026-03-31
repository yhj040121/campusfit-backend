package com.campusfit.modules.cooperation.vo;

import java.math.BigDecimal;

public record CooperationItemVO(
    String id,
    Long cooperationId,
    String title,
    String merchantName,
    String desc,
    String status,
    int statusCode,
    BigDecimal rewardAmount,
    int targetPostCount,
    int approvedPostCount,
    int submittedPostCount,
    int targetLikeCount,
    int approvedLikeCount,
    String deadlineAt,
    boolean canAccept,
    boolean accepted,
    boolean canPublish,
    boolean canAbandon,
    boolean rewardReady,
    boolean rewardIssued,
    String ruleText,
    String progressText
) {
}
