package com.campusfit.modules.admin.service;

import com.campusfit.modules.admin.vo.AdminContentAuditItemVO;
import com.campusfit.modules.admin.vo.AdminDashboardSummaryVO;
import com.campusfit.modules.admin.vo.AdminMerchantItemVO;
import com.campusfit.modules.admin.vo.AdminSettlementItemVO;
import com.campusfit.modules.admin.vo.AdminUserItemVO;
import com.campusfit.modules.admin.vo.AdminWithdrawRequestItemVO;

import java.util.List;

public interface AdminDashboardService {

    AdminDashboardSummaryVO getSummary();

    List<AdminUserItemVO> listUsers();

    List<AdminContentAuditItemVO> listContentAuditItems();

    List<AdminMerchantItemVO> listMerchants();

    List<AdminSettlementItemVO> listSettlements();

    List<AdminWithdrawRequestItemVO> listWithdrawRequests();

    void freezeUser(Long userId);

    void unfreezeUser(Long userId);

    void approvePost(Long postId);

    void rejectPost(Long postId);

    void settleCommission(Long recordId);

    void approveWithdrawRequest(Long requestId);

    void rejectWithdrawRequest(Long requestId);
}
