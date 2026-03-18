package com.campusfit.modules.message.vo;

public record MessageItemVO(
    String id,
    String type,
    String title,
    String desc,
    String time,
    boolean read
) {
}
