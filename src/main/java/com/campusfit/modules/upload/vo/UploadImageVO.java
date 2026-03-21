package com.campusfit.modules.upload.vo;

public record UploadImageVO(
    String url,
    String objectKey,
    String originalName,
    long size,
    String contentType
) {
}
