package com.campusfit.modules.admin.vo;

import java.math.BigDecimal;

public record AdminSettlementItemVO(
    Long recordId,
    String creator,
    String type,
    BigDecimal amount,
    String status,
    int statusCode,
    String createdAt
) {
}