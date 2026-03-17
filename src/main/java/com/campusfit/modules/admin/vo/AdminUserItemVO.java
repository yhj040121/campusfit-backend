package com.campusfit.modules.admin.vo;

public record AdminUserItemVO(
    Long userId,
    String nickname,
    String school,
    int posts,
    int favorites,
    String status,
    int statusCode
) {
}