package com.campusfit.modules.post.vo;

import java.util.List;

public record PostDetailVO(
    String id,
    String coverTag,
    String title,
    String subtitle,
    String desc,
    Long authorId,
    String user,
    String avatar,
    String avatarClass,
    String school,
    boolean mine,
    boolean liked,
    boolean favorited,
    boolean followed,
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
    String guideTip,
    List<String> highlights,
    List<String> commentsPreview
) {
}
