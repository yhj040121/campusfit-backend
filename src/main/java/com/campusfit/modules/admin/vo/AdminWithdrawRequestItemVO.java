package com.campusfit.modules.admin.vo;

import java.math.BigDecimal;

public record AdminWithdrawRequestItemVO(
    Long requestId,
    String creator,
    BigDecimal amount,
    String status,
    int statusCode,
    String createdAt,
    String processedAt,
    String remark
) {
}
