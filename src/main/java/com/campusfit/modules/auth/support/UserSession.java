package com.campusfit.modules.auth.support;

public record UserSession(
    String token,
    Long userId,
    String phone,
    String nickname
) {
}