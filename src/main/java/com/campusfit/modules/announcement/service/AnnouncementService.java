package com.campusfit.modules.announcement.service;

import com.campusfit.modules.announcement.vo.AnnouncementVO;

import java.util.List;

public interface AnnouncementService {

    List<AnnouncementVO> listPublishedHistory();

    AnnouncementVO getLatestPublished();
}
