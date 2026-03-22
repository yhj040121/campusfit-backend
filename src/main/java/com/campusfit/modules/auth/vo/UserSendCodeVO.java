package com.campusfit.modules.auth.vo;

public record UserSendCodeVO(
    String phone,
    String code,
    int expiresInSeconds,
    int retryAfterSeconds,
    boolean demo
) {
}
