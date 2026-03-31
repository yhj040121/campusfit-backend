package com.campusfit.modules.draft.vo;

import com.campusfit.modules.activity.vo.ActivityItemVO;
import com.campusfit.modules.cooperation.vo.CooperationItemVO;

import java.math.BigDecimal;
import java.util.List;

public record DraftItemVO(
    String id,
    String title,
    String note,
    String desc,
    List<String> tags,
    List<String> imageUrls,
    String productLink,
    BigDecimal productPrice,
    String savedAt,
    ActivityItemVO activity,
    CooperationItemVO cooperation
) {
}
