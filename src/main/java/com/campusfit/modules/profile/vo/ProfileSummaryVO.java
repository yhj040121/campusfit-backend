package com.campusfit.modules.profile.vo;

public record ProfileSummaryVO(
    String name,
    String avatar,
    String avatarUrl,
    String school,
    String sign,
    int following,
    int followers,
    int likes,
    String income,
    int cooperation
) {
}
