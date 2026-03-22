package com.campusfit.modules.draft.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record DraftSaveRequest(
    String draftId,
    @Size(max = 30, message = "草稿标题最多 30 个字符")
    String title,
    @Size(max = 200, message = "草稿描述最多 200 个字符")
    String desc,
    @Size(max = 9, message = "草稿图片最多保留 9 张")
    List<String> imageUrls,
    List<String> tags,
    String productLink,
    @DecimalMin(value = "0.01", message = "商品价格必须大于 0")
    @Digits(integer = 8, fraction = 2, message = "商品价格最多支持 8 位整数，并保留 2 位小数")
    BigDecimal productPrice,
    String activityId
) {
}
