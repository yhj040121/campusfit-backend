package com.campusfit.modules.post.vo;

public record PostProductJumpVO(
    String postId,
    String product,
    String platform,
    String price,
    String jumpUrl,
    String incentiveTip,
    String guideTip,
    String clickTip,
    int clickCount
) {
}
