package com.campusfit.modules.cooperation.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.cooperation.service.CooperationService;
import com.campusfit.modules.cooperation.vo.CooperationItemVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/cooperations")
public class CooperationController {

    private final CooperationService cooperationService;

    public CooperationController(CooperationService cooperationService) {
        this.cooperationService = cooperationService;
    }

    @GetMapping("/mine")
    public ApiResponse<List<CooperationItemVO>> listMine() {
        return ApiResponse.success(cooperationService.listMine());
    }

    @GetMapping("/{cooperationCode}")
    public ApiResponse<CooperationItemVO> detail(@PathVariable String cooperationCode) {
        return ApiResponse.success(cooperationService.findByCode(cooperationCode));
    }

    @PostMapping("/{cooperationCode}/accept")
    public ApiResponse<CooperationItemVO> accept(@PathVariable String cooperationCode) {
        return ApiResponse.success("Cooperation accepted", cooperationService.accept(cooperationCode));
    }

    @PostMapping("/{cooperationCode}/abandon")
    public ApiResponse<CooperationItemVO> abandon(@PathVariable String cooperationCode) {
        return ApiResponse.success("Cooperation abandoned", cooperationService.abandon(cooperationCode));
    }
}
