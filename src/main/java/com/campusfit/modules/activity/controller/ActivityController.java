package com.campusfit.modules.activity.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.activity.service.ActivityService;
import com.campusfit.modules.activity.vo.ActivityItemVO;
import com.campusfit.modules.activity.vo.ActivitySummaryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping
    public ApiResponse<List<ActivityItemVO>> listActivities() {
        return ApiResponse.success(activityService.listActivities());
    }

    @GetMapping("/featured")
    public ApiResponse<List<ActivityItemVO>> listFeaturedActivities() {
        return ApiResponse.success(activityService.listFeaturedActivities());
    }

    @GetMapping("/mine")
    public ApiResponse<List<ActivityItemVO>> listMyActivities() {
        return ApiResponse.success(activityService.listMyActivities());
    }

    @GetMapping("/summary")
    public ApiResponse<ActivitySummaryVO> getMySummary() {
        return ApiResponse.success(activityService.getMySummary());
    }

    @GetMapping("/{activityCode}")
    public ApiResponse<ActivityItemVO> getActivityDetail(@PathVariable String activityCode) {
        return ApiResponse.success(activityService.findByCode(activityCode));
    }

    @PostMapping("/{activityCode}/join-toggle")
    public ApiResponse<ActivityItemVO> toggleJoin(@PathVariable String activityCode) {
        return ApiResponse.success(activityService.toggleJoin(activityCode));
    }
}
