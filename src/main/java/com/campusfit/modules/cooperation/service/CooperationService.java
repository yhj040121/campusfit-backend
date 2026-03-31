package com.campusfit.modules.cooperation.service;

import com.campusfit.modules.cooperation.vo.CooperationItemVO;

import java.util.List;

public interface CooperationService {

    List<CooperationItemVO> listMine();

    CooperationItemVO accept(String cooperationCode);

    CooperationItemVO abandon(String cooperationCode);

    CooperationItemVO findByCode(String cooperationCode);

    CooperationItemVO findByPostCode(String postCode);

    void bindPostToCooperation(long postId, long userId, String cooperationCode);

    void syncProgressByPostId(long postId);

    void syncProgressByCooperationId(Long cooperationId);
}
