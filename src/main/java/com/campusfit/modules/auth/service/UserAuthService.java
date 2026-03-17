package com.campusfit.modules.auth.service;

import com.campusfit.modules.auth.dto.UserLoginRequest;
import com.campusfit.modules.auth.dto.UserRegisterRequest;
import com.campusfit.modules.auth.support.UserSession;
import com.campusfit.modules.auth.vo.UserAuthResultVO;
import com.campusfit.modules.auth.vo.UserSessionVO;

public interface UserAuthService {

    UserAuthResultVO login(UserLoginRequest request);

    UserAuthResultVO register(UserRegisterRequest request);

    UserSessionVO currentUser(UserSession session);

    UserSession findByToken(String token);

    void logout(String token);

    void refreshNickname(Long userId, String nickname);
}