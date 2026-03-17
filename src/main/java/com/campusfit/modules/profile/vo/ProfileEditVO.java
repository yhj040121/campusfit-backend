package com.campusfit.modules.profile.vo;

public record ProfileEditVO(
    String phone,
    String nickname,
    String schoolName,
    String gradeName,
    String signature
) {
}