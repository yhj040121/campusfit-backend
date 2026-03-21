package com.campusfit.modules.draft.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.draft.dto.DraftSaveRequest;
import com.campusfit.modules.draft.service.DraftService;
import com.campusfit.modules.draft.vo.DraftItemVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/drafts")
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    @GetMapping
    public ApiResponse<List<DraftItemVO>> listMine() {
        return ApiResponse.success(draftService.listMine());
    }

    @GetMapping("/{draftId}")
    public ApiResponse<DraftItemVO> detail(@PathVariable String draftId) {
        return ApiResponse.success(draftService.getDetail(draftId));
    }

    @PostMapping
    public ApiResponse<DraftItemVO> save(@Valid @RequestBody DraftSaveRequest request) {
        return ApiResponse.success("草稿已保存", draftService.save(request));
    }

    @PutMapping("/{draftId}")
    public ApiResponse<DraftItemVO> update(@PathVariable String draftId, @Valid @RequestBody DraftSaveRequest request) {
        return ApiResponse.success("草稿已更新", draftService.update(draftId, request));
    }

    @DeleteMapping("/{draftId}")
    public ApiResponse<Boolean> delete(@PathVariable String draftId) {
        return ApiResponse.success("草稿已删除", draftService.delete(draftId));
    }

    @PostMapping("/{draftId}/publish")
    public ApiResponse<PostCreateResultVO> publish(
        @PathVariable String draftId,
        @Valid @RequestBody(required = false) DraftSaveRequest request
    ) {
        return ApiResponse.success("草稿已发布", draftService.publish(draftId, request));
    }
}
