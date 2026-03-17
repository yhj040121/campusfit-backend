package com.campusfit.modules.tag.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.modules.tag.service.TagService;
import com.campusfit.modules.tag.vo.TagOptionsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping("/options")
    public ApiResponse<TagOptionsVO> options() {
        return ApiResponse.success(tagService.getOptions());
    }
}
