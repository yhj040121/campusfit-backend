package com.campusfit.modules.profile.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record IncentiveWithdrawRequest(
    @DecimalMin(value = "0.01", message = "提现金额必须大于 0")
    BigDecimal amount
) {
}
