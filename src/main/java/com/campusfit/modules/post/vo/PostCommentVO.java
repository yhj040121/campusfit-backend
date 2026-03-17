package com.campusfit.modules.post.vo;

public record PostCommentVO(
    String id,
    String name,
    String avatar,
    String avatarClass,
    String text,
    String time,
    int likes
) {
}
