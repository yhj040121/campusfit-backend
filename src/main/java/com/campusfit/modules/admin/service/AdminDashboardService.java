package com.campusfit.modules.admin.service;

import com.campusfit.modules.admin.dto.AdminActivitySaveRequest;
import com.campusfit.modules.admin.dto.AdminAnnouncementSaveRequest;
import com.campusfit.modules.admin.dto.AdminCooperationSaveRequest;
import com.campusfit.modules.admin.dto.AdminMerchantSaveRequest;
import com.campusfit.modules.admin.vo.AdminActivityItemVO;
import com.campusfit.modules.admin.vo.AdminAnnouncementItemVO;
import com.campusfit.modules.admin.vo.AdminCooperationItemVO;
import com.campusfit.modules.admin.vo.AdminContentAuditDetailVO;
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

    AdminContentAuditDetailVO getContentAuditDetail(Long postId);

    List<AdminAnnouncementItemVO> listAnnouncements();

    List<AdminActivityItemVO> listActivities();

    List<AdminCooperationItemVO> listCooperations();

    List<AdminMerchantItemVO> listMerchants();

    List<AdminSettlementItemVO> listSettlements();

    List<AdminWithdrawRequestItemVO> listWithdrawRequests();

    void freezeUser(Long userId);

    void unfreezeUser(Long userId);

    void approvePost(Long postId);

    void rejectPost(Long postId);

    void createAnnouncement(AdminAnnouncementSaveRequest request, String operatorName);

    void updateAnnouncement(Long announcementId, AdminAnnouncementSaveRequest request, String operatorName);

    void enableAnnouncement(Long announcementId, String operatorName);

    void disableAnnouncement(Long announcementId, String operatorName);

    void createActivity(AdminActivitySaveRequest request);

    void updateActivity(Long activityId, AdminActivitySaveRequest request);

    void createCooperation(AdminCooperationSaveRequest request);

    void createMerchant(AdminMerchantSaveRequest request);

    void activateMerchant(Long merchantId);

    void cancelCooperation(Long cooperationId);

    void deleteMerchant(Long merchantId);

    void startActivity(Long activityId);

    void stopActivity(Long activityId);

    void issueCooperationReward(Long cooperationId);

    void settleCommission(Long recordId);

    void approveWithdrawRequest(Long requestId);

    void rejectWithdrawRequest(Long requestId);
}
