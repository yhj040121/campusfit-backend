package com.campusfit.modules.post.vo;

public record PostCommentVO(
    String id,
    String parentId,
    String name,
    String avatar,
    String avatarUrl,
    String avatarClass,
    String text,
    String time,
    int likes,
    boolean mine,
    boolean liked,
    String replyToName,
    java.util.List<PostCommentVO> replies
) {
}
