package com.campusfit.modules.profile.service;

import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.profile.dto.ProfileUpdateRequest;
import com.campusfit.modules.profile.vo.FollowToggleVO;
import com.campusfit.modules.profile.vo.ProfileEditVO;
import com.campusfit.modules.profile.vo.ProfileSummaryVO;

import java.util.List;

public interface ProfileService {

    ProfileSummaryVO getCurrentProfile();

    ProfileEditVO getCurrentProfileForEdit();

    ProfileSummaryVO updateCurrentProfile(ProfileUpdateRequest request);

    List<UserCardVO> listFollows(String type);

    FollowToggleVO toggleFollow(Long targetUserId);
}