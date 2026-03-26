package com.campusfit.modules.post.vo;

import com.campusfit.modules.activity.vo.ActivityItemVO;

import java.util.List;

public record PostDetailVO(
    String id,
    String coverTag,
    String title,
    String subtitle,
    String publishTime,
    String desc,
    String coverImageUrl,
    List<String> imageUrls,
    Long authorId,
    String user,
    String avatar,
    String avatarUrl,
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
    String productLink,
    String profit,
    String guideTip,
    ActivityItemVO activity,
    List<String> highlights,
    List<PostCommentVO> commentsPreview
) {
}
