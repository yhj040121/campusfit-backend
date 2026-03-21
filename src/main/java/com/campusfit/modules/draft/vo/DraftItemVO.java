package com.campusfit.modules.draft.vo;

import com.campusfit.modules.activity.vo.ActivityItemVO;

import java.util.List;

public record DraftItemVO(
    String id,
    String title,
    String note,
    String desc,
    List<String> tags,
    List<String> imageUrls,
    String productLink,
    String savedAt,
    ActivityItemVO activity
) {
}
