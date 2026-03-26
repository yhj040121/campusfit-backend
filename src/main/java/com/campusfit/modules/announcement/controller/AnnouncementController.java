package com.campusfit.modules.announcement.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.announcement.service.AnnouncementService;
import com.campusfit.modules.announcement.vo.AnnouncementVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public ApiResponse<List<AnnouncementVO>> list() {
        return ApiResponse.success(announcementService.listPublishedHistory());
    }

    @GetMapping("/latest")
    public ApiResponse<AnnouncementVO> latest() {
        return ApiResponse.success(announcementService.getLatestPublished());
    }

    @GetMapping("/{announcementId}")
    public ApiResponse<AnnouncementVO> detail(@PathVariable Long announcementId) {
        return ApiResponse.success(announcementService.getPublishedDetail(announcementId));
    }
}
