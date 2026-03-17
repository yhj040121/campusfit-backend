package com.campusfit.modules.admin.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.config.AdminAuthInterceptor;
import com.campusfit.modules.admin.service.AdminAuthService;
import com.campusfit.modules.admin.service.AdminDashboardService;
import com.campusfit.modules.admin.support.AdminSession;
import com.campusfit.modules.admin.vo.AdminContentAuditItemVO;
import com.campusfit.modules.admin.vo.AdminDashboardSummaryVO;
import com.campusfit.modules.admin.vo.AdminMerchantItemVO;
import com.campusfit.modules.admin.vo.AdminSettlementItemVO;
import com.campusfit.modules.admin.vo.AdminUserItemVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    private AdminSession currentSession(HttpServletRequest request) {
        return (AdminSession) request.getAttribute(AdminAuthInterceptor.ADMIN_SESSION_ATTR);
    }
}