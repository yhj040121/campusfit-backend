package com.campusfit.modules.post.vo;

import com.campusfit.modules.activity.vo.ActivityItemVO;
import com.campusfit.modules.cooperation.vo.CooperationItemVO;

import java.math.BigDecimal;
import java.util.List;

public record PostEditVO(
    String id,
    String title,
    String desc,
    List<String> imageUrls,
    List<String> tags,
    String productLink,
    BigDecimal productPrice,
    ActivityItemVO activity,
    CooperationItemVO cooperation
) {
}
