package com.campusfit.modules.profile.vo;

public record ProfileIncentiveRecordVO(
    Long recordId,
    Long postId,
    String postTitle,
    String type,
    String amount,
    String status,
    int statusCode,
    String createdAt
) {
}
