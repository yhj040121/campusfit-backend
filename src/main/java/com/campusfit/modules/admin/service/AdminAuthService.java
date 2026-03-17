package com.campusfit.modules.admin.service;

import com.campusfit.modules.admin.dto.AdminLoginRequest;
import com.campusfit.modules.admin.support.AdminSession;
import com.campusfit.modules.admin.vo.AdminLoginVO;
import com.campusfit.modules.admin.vo.AdminProfileVO;

public interface AdminAuthService {

    AdminLoginVO login(AdminLoginRequest request);

    AdminProfileVO getProfile(AdminSession session);

    AdminSession findByToken(String token);

    void logout(String token);

    void requireAnyRole(AdminSession session, String... roleCodes);
}
