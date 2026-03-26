package com.campusfit.modules.profile.vo;

public record ProfileSummaryVO(
    String name,
    String avatar,
    String avatarUrl,
    String coverImageUrl,
    String school,
    String gender,
    String email,
    String locationName,
    String sign,
    int following,
    int followers,
    int likes,
    String income,
    int cooperation
) {
}
