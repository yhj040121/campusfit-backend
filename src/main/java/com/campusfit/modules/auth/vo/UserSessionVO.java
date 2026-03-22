package com.campusfit.modules.auth.vo;

public record UserSessionVO(
    Long userId,
    String phone,
    String nickname,
    String avatarUrl
) {
}
