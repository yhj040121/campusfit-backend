package com.campusfit.modules.profile.vo;

public record ProfileEditVO(
    String phone,
    String nickname,
    String avatarUrl,
    String coverImageUrl,
    String gender,
    String email,
    String locationName,
    String schoolName,
    String gradeName,
    String signature
) {
}
