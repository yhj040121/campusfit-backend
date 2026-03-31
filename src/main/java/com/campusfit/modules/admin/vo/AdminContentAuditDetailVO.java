package com.campusfit.modules.admin.vo;

import java.util.List;

public record AdminContentAuditDetailVO(
    Long postId,
    String postCode,
    String title,
    String description,
    String coverTag,
    String coverImageUrl,
    List<String> imageUrls,
    String author,
    String avatarUrl,
    String avatarText,
    String school,
    String scene,
    String style,
    String budget,
    int likeCount,
    int commentCount,
    int favoriteCount,
    int shareCount,
    boolean cooperationBound,
    String cooperationTitle,
    String cooperationMerchant,
    String cooperationReward,
    String cooperationStatus,
    String productStatus,
    boolean productConfigured,
    String productName,
    String productPlatform,
    String productPrice,
    String productUrl,
    String auditStatus,
    int auditStatusCode,
    String createdAt
) {
}
