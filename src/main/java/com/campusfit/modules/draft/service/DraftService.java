package com.campusfit.modules.draft.service;

import com.campusfit.modules.draft.dto.DraftSaveRequest;
import com.campusfit.modules.draft.vo.DraftItemVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;

import java.util.List;

public interface DraftService {

    List<DraftItemVO> listMine();

    DraftItemVO getDetail(String draftId);

    DraftItemVO save(DraftSaveRequest request);

    DraftItemVO update(String draftId, DraftSaveRequest request);

    boolean delete(String draftId);

    PostCreateResultVO publish(String draftId, DraftSaveRequest request);
}
