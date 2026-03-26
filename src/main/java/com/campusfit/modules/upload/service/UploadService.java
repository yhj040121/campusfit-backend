package com.campusfit.modules.upload.service;

import com.campusfit.modules.upload.vo.UploadImageVO;
import org.springframework.web.multipart.MultipartFile;

public interface UploadService {

    UploadImageVO uploadImage(MultipartFile file);

    UploadImageVO uploadAvatar(MultipartFile file);

    UploadImageVO uploadProfileCover(MultipartFile file);
}
