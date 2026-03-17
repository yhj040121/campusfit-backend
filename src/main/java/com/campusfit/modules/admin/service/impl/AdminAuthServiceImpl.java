package com.campusfit.modules.admin.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.admin.dto.AdminLoginRequest;
import com.campusfit.modules.admin.service.AdminAuthService;
import com.campusfit.modules.admin.support.AdminSession;
import com.campusfit.modules.admin.vo.AdminLoginVO;
import com.campusfit.modules.admin.vo.AdminProfileVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, AdminSession> sessionStore = new ConcurrentHashMap<>();

    public AdminAuthServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AdminLoginVO login(AdminLoginRequest request) {
        List<AdminAccountRecord> records = jdbcTemplate.query(
            "select id, username, password_hash, role_code, status from sys_admin_user where username = ?",
            (rs, rowNum) -> new AdminAccountRecord(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("role_code"),
                rs.getInt("status")
            ),
            request.username()
        );
        if (records.isEmpty()) {
            throw new BusinessException("Invalid username or password");
        }
        AdminAccountRecord account = records.get(0);
        if (account.status() != 1) {
            throw new BusinessException("Admin account is disabled");
        }
        if (!account.passwordHash().equals(request.password())) {
            throw new BusinessException("Invalid username or password");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        AdminSession session = new AdminSession(
            token,
            account.id(),
            account.username(),
            account.roleCode(),
            buildDisplayName(account.username(), account.roleCode())
        );
        sessionStore.put(token, session);
        return new AdminLoginVO(session.token(), session.username(), session.roleCode(), session.displayName());
    }

    @Override
    public AdminProfileVO getProfile(AdminSession session) {
        if (session == null) {
            throw new BusinessException("Admin is not logged in");
        }
        return new AdminProfileVO(session.username(), session.roleCode(), session.displayName());
    }

    @Override
    public AdminSession findByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessionStore.get(token);
    }

    @Override
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessionStore.remove(token);
        }
    }

    @Override
    public void requireAnyRole(AdminSession session, String... roleCodes) {
        if (session == null) {
            throw new BusinessException("Admin is not logged in");
        }
        if (roleCodes == null || roleCodes.length == 0) {
            return;
        }
        boolean matched = Arrays.stream(roleCodes).anyMatch(role -> role.equalsIgnoreCase(session.roleCode()));
        if (!matched) {
            throw new BusinessException("You do not have permission to access this page");
        }
    }

    private String buildDisplayName(String username, String roleCode) {
        return switch (roleCode) {
            case "SUPER_ADMIN" -> "Super Admin - " + username;
            case "CONTENT_OPERATOR" -> "Content Operator - " + username;
            case "FINANCE" -> "Finance - " + username;
            default -> "Admin - " + username;
        };
    }

    private record AdminAccountRecord(Long id, String username, String passwordHash, String roleCode, int status) {
    }
}