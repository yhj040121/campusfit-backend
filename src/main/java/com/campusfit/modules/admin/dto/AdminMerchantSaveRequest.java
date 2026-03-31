package com.campusfit.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminMerchantSaveRequest(
    @NotBlank(message = "商家名称不能为空")
    @Size(max = 120, message = "商家名称最多 120 个字符")
    String merchantName,

    @Size(max = 50, message = "联系人最多 50 个字符")
    String contactName,

    @Size(max = 30, message = "联系电话最多 30 个字符")
    String contactPhone,

    Integer cooperationStatus,

    @Size(max = 255, message = "备注最多 255 个字符")
    String remark
) {
}
