package com.campusfit.modules.activity.service;

import com.campusfit.modules.activity.vo.ActivityItemVO;
import com.campusfit.modules.activity.vo.ActivitySummaryVO;

import java.util.List;

public interface ActivityService {

    List<ActivityItemVO> listActivities();

    List<ActivityItemVO> listFeaturedActivities();

    List<ActivityItemVO> listMyActivities();

    ActivitySummaryVO getMySummary();

    ActivityItemVO toggleJoin(String activityCode);

    ActivityItemVO findByPostCode(String postCode);

    ActivityItemVO findByCode(String activityCode);

    void bindPostToActivity(long postId, long userId, String activityCode);
}
