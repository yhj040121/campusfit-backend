package com.campusfit.modules.admin.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.config.AdminAuthInterceptor;
import com.campusfit.modules.admin.dto.AdminLoginRequest;
import com.campusfit.modules.admin.service.AdminAuthService;
import com.campusfit.modules.admin.support.AdminSession;
import com.campusfit.modules.admin.vo.AdminLoginVO;
import com.campusfit.modules.admin.vo.AdminProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.success("登录成功", adminAuthService.login(request));
    }

    @GetMapping("/me")
    public ApiResponse<AdminProfileVO> me(HttpServletRequest request) {
        return ApiResponse.success(adminAuthService.getProfile(currentSession(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        AdminSession session = currentSession(request);
        adminAuthService.logout(session.token());
        return ApiResponse.success("已退出登录", null);
    }

    private AdminSession currentSession(HttpServletRequest request) {
        return (AdminSession) request.getAttribute(AdminAuthInterceptor.ADMIN_SESSION_ATTR);
    }
}
