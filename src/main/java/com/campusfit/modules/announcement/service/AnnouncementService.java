package com.campusfit.modules.announcement.service;

import com.campusfit.modules.announcement.vo.AnnouncementVO;

public interface AnnouncementService {

    AnnouncementVO getLatestPublished();

    AnnouncementVO getPublishedDetail(Long announcementId);
}
