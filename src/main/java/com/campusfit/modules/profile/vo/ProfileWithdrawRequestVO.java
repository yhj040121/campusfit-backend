package com.campusfit.modules.profile.vo;

public record ProfileWithdrawRequestVO(
    Long requestId,
    String amount,
    String status,
    int statusCode,
    String createdAt,
    String processedAt,
    String remark
) {
}
