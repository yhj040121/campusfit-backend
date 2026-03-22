package com.campusfit.modules.admin.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.config.AdminAuthInterceptor;
import com.campusfit.modules.admin.dto.AdminActivitySaveRequest;
import com.campusfit.modules.admin.dto.AdminAnnouncementSaveRequest;
import com.campusfit.modules.admin.service.AdminAuthService;
import com.campusfit.modules.admin.service.AdminDashboardService;
import com.campusfit.modules.admin.support.AdminSession;
import com.campusfit.modules.admin.vo.AdminActivityItemVO;
import com.campusfit.modules.admin.vo.AdminAnnouncementItemVO;
import com.campusfit.modules.admin.vo.AdminContentAuditItemVO;
import com.campusfit.modules.admin.vo.AdminDashboardSummaryVO;
import com.campusfit.modules.admin.vo.AdminMerchantItemVO;
import com.campusfit.modules.admin.vo.AdminSettlementItemVO;
import com.campusfit.modules.admin.vo.AdminUserItemVO;
import com.campusfit.modules.admin.vo.AdminWithdrawRequestItemVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;
    private final AdminAuthService adminAuthService;

    public AdminDashboardController(AdminDashboardService adminDashboardService, AdminAuthService adminAuthService) {
        this.adminDashboardService = adminDashboardService;
        this.adminAuthService = adminAuthService;
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<AdminDashboardSummaryVO> summary(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR", "FINANCE");
        return ApiResponse.success(adminDashboardService.getSummary());
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserItemVO>> users(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN");
        return ApiResponse.success(adminDashboardService.listUsers());
    }

    @PostMapping("/users/{userId}/freeze")
    public ApiResponse<Void> freezeUser(@PathVariable Long userId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN");
        adminDashboardService.freezeUser(userId);
        return ApiResponse.success("User frozen", null);
    }

    @PostMapping("/users/{userId}/unfreeze")
    public ApiResponse<Void> unfreezeUser(@PathVariable Long userId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN");
        adminDashboardService.unfreezeUser(userId);
        return ApiResponse.success("User reactivated", null);
    }

    @GetMapping("/content-audit")
    public ApiResponse<List<AdminContentAuditItemVO>> contentAudit(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        return ApiResponse.success(adminDashboardService.listContentAuditItems());
    }

    @PostMapping("/content-audit/{postId}/approve")
    public ApiResponse<Void> approvePost(@PathVariable Long postId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.approvePost(postId);
        return ApiResponse.success("Post approved", null);
    }

    @PostMapping("/content-audit/{postId}/reject")
    public ApiResponse<Void> rejectPost(@PathVariable Long postId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.rejectPost(postId);
        return ApiResponse.success("Post rejected", null);
    }

    @GetMapping("/announcements")
    public ApiResponse<List<AdminAnnouncementItemVO>> announcements(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        return ApiResponse.success(adminDashboardService.listAnnouncements());
    }

    @PostMapping("/announcements")
    public ApiResponse<Void> createAnnouncement(
        @Valid @RequestBody AdminAnnouncementSaveRequest payload,
        HttpServletRequest request
    ) {
        AdminSession session = currentSession(request);
        adminAuthService.requireAnyRole(session, "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.createAnnouncement(payload, session.displayName());
        return ApiResponse.success("Announcement created", null);
    }

    @PutMapping("/announcements/{announcementId}")
    public ApiResponse<Void> updateAnnouncement(
        @PathVariable Long announcementId,
        @Valid @RequestBody AdminAnnouncementSaveRequest payload,
        HttpServletRequest request
    ) {
        AdminSession session = currentSession(request);
        adminAuthService.requireAnyRole(session, "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.updateAnnouncement(announcementId, payload, session.displayName());
        return ApiResponse.success("Announcement updated", null);
    }

    @PostMapping("/announcements/{announcementId}/enable")
    public ApiResponse<Void> enableAnnouncement(@PathVariable Long announcementId, HttpServletRequest request) {
        AdminSession session = currentSession(request);
        adminAuthService.requireAnyRole(session, "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.enableAnnouncement(announcementId, session.displayName());
        return ApiResponse.success("Announcement enabled", null);
    }

    @PostMapping("/announcements/{announcementId}/disable")
    public ApiResponse<Void> disableAnnouncement(@PathVariable Long announcementId, HttpServletRequest request) {
        AdminSession session = currentSession(request);
        adminAuthService.requireAnyRole(session, "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.disableAnnouncement(announcementId, session.displayName());
        return ApiResponse.success("Announcement disabled", null);
    }

    @GetMapping("/activities")
    public ApiResponse<List<AdminActivityItemVO>> activities(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        return ApiResponse.success(adminDashboardService.listActivities());
    }

    @PostMapping("/activities")
    public ApiResponse<Void> createActivity(
        @Valid @RequestBody AdminActivitySaveRequest payload,
        HttpServletRequest request
    ) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.createActivity(payload);
        return ApiResponse.success("Activity created", null);
    }

    @PutMapping("/activities/{activityId}")
    public ApiResponse<Void> updateActivity(
        @PathVariable Long activityId,
        @Valid @RequestBody AdminActivitySaveRequest payload,
        HttpServletRequest request
    ) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.updateActivity(activityId, payload);
        return ApiResponse.success("Activity updated", null);
    }

    @PostMapping("/activities/{activityId}/start")
    public ApiResponse<Void> startActivity(@PathVariable Long activityId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.startActivity(activityId);
        return ApiResponse.success("Activity started", null);
    }

    @PostMapping("/activities/{activityId}/stop")
    public ApiResponse<Void> stopActivity(@PathVariable Long activityId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        adminDashboardService.stopActivity(activityId);
        return ApiResponse.success("Activity stopped", null);
    }

    @GetMapping("/merchants")
    public ApiResponse<List<AdminMerchantItemVO>> merchants(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "CONTENT_OPERATOR");
        return ApiResponse.success(adminDashboardService.listMerchants());
    }

    @GetMapping("/settlements")
    public ApiResponse<List<AdminSettlementItemVO>> settlements(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "FINANCE");
        return ApiResponse.success(adminDashboardService.listSettlements());
    }

    @PostMapping("/settlements/{recordId}/confirm")
    public ApiResponse<Void> settleCommission(@PathVariable Long recordId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "FINANCE");
        adminDashboardService.settleCommission(recordId);
        return ApiResponse.success("Settlement confirmed", null);
    }

    @GetMapping("/withdraw-requests")
    public ApiResponse<List<AdminWithdrawRequestItemVO>> withdrawRequests(HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "FINANCE");
        return ApiResponse.success(adminDashboardService.listWithdrawRequests());
    }

    @PostMapping("/withdraw-requests/{requestId}/approve")
    public ApiResponse<Void> approveWithdrawRequest(@PathVariable Long requestId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "FINANCE");
        adminDashboardService.approveWithdrawRequest(requestId);
        return ApiResponse.success("Withdraw request approved", null);
    }

    @PostMapping("/withdraw-requests/{requestId}/reject")
    public ApiResponse<Void> rejectWithdrawRequest(@PathVariable Long requestId, HttpServletRequest request) {
        adminAuthService.requireAnyRole(currentSession(request), "SUPER_ADMIN", "FINANCE");
        adminDashboardService.rejectWithdrawRequest(requestId);
        return ApiResponse.success("Withdraw request rejected", null);
    }

    private AdminSession currentSession(HttpServletRequest request) {
        return (AdminSession) request.getAttribute(AdminAuthInterceptor.ADMIN_SESSION_ATTR);
    }
}
