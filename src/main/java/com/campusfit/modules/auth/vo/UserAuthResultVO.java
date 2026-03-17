package com.campusfit.modules.auth.vo;

public record UserAuthResultVO(
    String token,
    Long userId,
    String phone,
    String nickname
) {
}