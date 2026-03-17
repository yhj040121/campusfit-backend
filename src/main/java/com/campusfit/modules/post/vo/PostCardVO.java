package com.campusfit.modules.post.vo;

public record PostCardVO(
    String id,
    String coverTag,
    String title,
    String subtitle,
    String desc,
    String user,
    String avatar,
    String avatarClass,
    String school,
    String scene,
    String style,
    String budget,
    int likes,
    int comments,
    int saves,
    int shares,
    String price,
    String product,
    String platform,
    String profit,
    String guideTip
) {
}
