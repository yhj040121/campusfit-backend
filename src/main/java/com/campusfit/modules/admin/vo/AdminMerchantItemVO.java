package com.campusfit.modules.admin.vo;

public record AdminMerchantItemVO(
    Long merchantId,
    String name,
    String contact,
    int campaigns,
    String status
) {
}
