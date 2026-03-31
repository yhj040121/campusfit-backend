package com.campusfit.modules.admin.vo;

import java.math.BigDecimal;

public record AdminMerchantItemVO(
    Long merchantId,
    String name,
    String contact,
    String phone,
    int campaigns,
    int cooperations,
    BigDecimal pendingRewardAmount,
    BigDecimal issuedRewardAmount,
    String status,
    int statusCode,
    boolean canDelete
) {
}
