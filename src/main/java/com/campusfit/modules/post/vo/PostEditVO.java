package com.campusfit.modules.post.vo;

import java.util.List;

public record PostEditVO(
    String id,
    String title,
    String desc,
    List<String> imageUrls,
    List<String> tags,
    String productLink
) {
}
