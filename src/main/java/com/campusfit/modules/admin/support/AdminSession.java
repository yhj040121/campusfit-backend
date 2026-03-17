package com.campusfit.modules.admin.support;

public record AdminSession(
    String token,
    Long adminId,
    String username,
    String roleCode,
    String displayName
) {
}
