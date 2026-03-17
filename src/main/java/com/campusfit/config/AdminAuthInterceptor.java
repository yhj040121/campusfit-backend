package com.campusfit.config;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.admin.service.AdminAuthService;
import com.campusfit.modules.admin.support.AdminSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String ADMIN_SESSION_ATTR = "campusfit.admin.session";

    private final AdminAuthService adminAuthService;
    private final ObjectMapper objectMapper;

    public AdminAuthInterceptor(AdminAuthService adminAuthService, ObjectMapper objectMapper) {
        this.adminAuthService = adminAuthService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/admin")) {
            return true;
        }
        if (requestUri.equals("/api/admin/auth/login")) {
            return true;
        }
        String token = extractToken(request);
        AdminSession session = adminAuthService.findByToken(token);
        if (session == null) {
            writeUnauthorized(response);
            return false;
        }
        request.setAttribute(ADMIN_SESSION_ATTR, session);
        return true;
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("X-Admin-Token");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure("Admin session expired")));
    }
}