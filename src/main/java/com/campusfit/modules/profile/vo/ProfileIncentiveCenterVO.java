package com.campusfit.modules.profile.vo;

import java.util.List;

public record ProfileIncentiveCenterVO(
    String totalAmount,
    String availableAmount,
    String availableAmountRaw,
    String pendingSettlementAmount,
    String pendingWithdrawAmount,
    String withdrawnAmount,
    int settledCount,
    int pendingCount,
    boolean canWithdraw,
    String withdrawHint,
    List<ProfileIncentiveRecordVO> settlementRecords,
    List<ProfileWithdrawRequestVO> withdrawRequests
) {
}
