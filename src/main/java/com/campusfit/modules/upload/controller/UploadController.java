package com.campusfit.modules.upload.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.upload.service.UploadService;
import com.campusfit.modules.upload.vo.UploadImageVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/images")
    public ApiResponse<UploadImageVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return ApiResponse.success("图片上传成功", uploadService.uploadImage(file));
    }
}
