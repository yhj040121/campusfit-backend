package com.campusfit.common.vo;

public record UserCardVO(
    Long userId,
    String name,
    String avatar,
    String avatarClass,
    String intro,
    boolean active
) {
}
