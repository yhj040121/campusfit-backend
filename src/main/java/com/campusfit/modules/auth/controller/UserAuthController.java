package com.campusfit.modules.auth.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.auth.dto.UserLoginRequest;
import com.campusfit.modules.auth.dto.UserRegisterRequest;
import com.campusfit.modules.auth.dto.UserSendCodeRequest;
import com.campusfit.modules.auth.service.UserAuthService;
import com.campusfit.modules.auth.support.UserSession;
import com.campusfit.modules.auth.vo.UserAuthResultVO;
import com.campusfit.modules.auth.vo.UserSendCodeVO;
import com.campusfit.modules.auth.vo.UserSessionVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class UserAuthController {

    public static final String USER_SESSION_ATTR = "campusfit.user.session";

    private final UserAuthService userAuthService;

    public UserAuthController(UserAuthService userAuthService) {
        this.userAuthService = userAuthService;
    }

    @PostMapping("/send-code")
    public ApiResponse<UserSendCodeVO> sendCode(@Valid @RequestBody UserSendCodeRequest request) {
        return ApiResponse.success("Verification code sent", userAuthService.sendCode(request));
    }

    @PostMapping("/login")
    public ApiResponse<UserAuthResultVO> login(@Valid @RequestBody UserLoginRequest request) {
        return ApiResponse.success("Login success", userAuthService.login(request));
    }

    @PostMapping("/register")
    public ApiResponse<UserAuthResultVO> register(@Valid @RequestBody UserRegisterRequest request) {
        return ApiResponse.success("Registration success", userAuthService.register(request));
    }

    @GetMapping("/me")
    public ApiResponse<UserSessionVO> me(HttpServletRequest request) {
        return ApiResponse.success(userAuthService.currentUser(currentSession(request)));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        UserSession session = currentSession(request);
        userAuthService.logout(session == null ? null : session.token());
        return ApiResponse.success("Logout success", null);
    }

    private UserSession currentSession(HttpServletRequest request) {
        return (UserSession) request.getAttribute(USER_SESSION_ATTR);
    }
}
