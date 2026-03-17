package com.campusfit.modules.admin.vo;

import java.math.BigDecimal;

public record AdminDashboardSummaryVO(
    int todayUsers,
    int pendingAudits,
    int productClicks,
    BigDecimal estimatedCommission,
    int activeCampaigns
) {
}
