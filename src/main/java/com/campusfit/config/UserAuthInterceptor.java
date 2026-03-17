package com.campusfit.config;

import com.campusfit.modules.auth.controller.UserAuthController;
import com.campusfit.modules.auth.service.UserAuthService;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.auth.support.UserSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserAuthInterceptor implements HandlerInterceptor {

    private final UserAuthService userAuthService;

    public UserAuthInterceptor(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String requestUri = request.getRequestURI();
        if (!requestUri.startsWith("/api/") || requestUri.startsWith("/api/admin/")) {
            return true;
        }
        String token = extractToken(request);
        UserSession session = userAuthService.findByToken(token);
        if (session != null) {
            request.setAttribute(UserAuthController.USER_SESSION_ATTR, session);
            UserAuthContext.set(session);
        } else {
            UserAuthContext.clear();
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserAuthContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return request.getHeader("X-User-Token");
    }
}