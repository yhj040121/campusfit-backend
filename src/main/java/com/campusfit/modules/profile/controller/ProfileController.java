package com.campusfit.modules.profile.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.profile.dto.ProfileUpdateRequest;
import com.campusfit.modules.profile.service.ProfileService;
import com.campusfit.modules.profile.vo.FollowToggleVO;
import com.campusfit.modules.profile.vo.ProfileEditVO;
import com.campusfit.modules.profile.vo.ProfileSummaryVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public ApiResponse<ProfileSummaryVO> me() {
        return ApiResponse.success(profileService.getCurrentProfile());
    }

    @GetMapping("/me/edit")
    public ApiResponse<ProfileEditVO> editForm() {
        return ApiResponse.success(profileService.getCurrentProfileForEdit());
    }

    @PutMapping("/me")
    public ApiResponse<ProfileSummaryVO> update(@Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.success("Profile updated", profileService.updateCurrentProfile(request));
    }

    @GetMapping("/follows")
    public ApiResponse<List<UserCardVO>> follows(@RequestParam(defaultValue = "following") String type) {
        return ApiResponse.success(profileService.listFollows(type));
    }

    @PostMapping("/follows/{targetUserId}")
    public ApiResponse<FollowToggleVO> toggleFollow(@PathVariable Long targetUserId) {
        return ApiResponse.success(profileService.toggleFollow(targetUserId));
    }
}