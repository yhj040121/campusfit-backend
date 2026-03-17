package com.campusfit.modules.admin.vo;

public record AdminLoginVO(
    String token,
    String username,
    String roleCode,
    String displayName
) {
}
