package com.campusfit.modules.post.controller;

import com.campusfit.common.api.ApiResponse;
import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.post.dto.PostCommentCreateRequest;
import com.campusfit.modules.post.dto.PostCreateRequest;
import com.campusfit.modules.post.service.PostService;
import com.campusfit.modules.post.vo.PostCardVO;
import com.campusfit.modules.post.vo.PostCommentVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import com.campusfit.modules.post.vo.PostDetailVO;
import com.campusfit.modules.post.vo.PostInteractionVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/recommendations")
    public ApiResponse<List<PostCardVO>> recommendations() {
        return ApiResponse.success(postService.listRecommendations());
    }

    @GetMapping("/mine")
    public ApiResponse<List<PostCardVO>> mine() {
        return ApiResponse.success(postService.listMine());
    }

    @GetMapping("/favorites")
    public ApiResponse<List<PostCardVO>> favorites() {
        return ApiResponse.success(postService.listFavorites());
    }

    @GetMapping("/search")
    public ApiResponse<List<PostCardVO>> search(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(defaultValue = "") String scene,
        @RequestParam(defaultValue = "") String style,
        @RequestParam(defaultValue = "") String budget
    ) {
        return ApiResponse.success(postService.search(keyword, scene, style, budget));
    }

    @GetMapping("/{postId}")
    public ApiResponse<PostDetailVO> detail(@PathVariable String postId) {
        return ApiResponse.success(postService.getDetail(postId));
    }

    @PostMapping
    public ApiResponse<PostCreateResultVO> create(@Valid @RequestBody PostCreateRequest request) {
        return ApiResponse.success("????", postService.create(request));
    }

    @PostMapping("/{postId}/delete")
    public ApiResponse<Boolean> delete(@PathVariable String postId) {
        postService.deleteMine(postId);
        return ApiResponse.success("????", true);
    }

    @GetMapping("/{postId}/comments")
    public ApiResponse<List<PostCommentVO>> comments(@PathVariable String postId) {
        return ApiResponse.success(postService.listComments(postId));
    }

    @PostMapping("/{postId}/comments")
    public ApiResponse<PostCommentVO> createComment(@PathVariable String postId, @Valid @RequestBody PostCommentCreateRequest request) {
        return ApiResponse.success("????", postService.createComment(postId, request));
    }

    @GetMapping("/{postId}/likes")
    public ApiResponse<List<UserCardVO>> likes(@PathVariable String postId) {
        return ApiResponse.success(postService.listLikeUsers(postId));
    }

    @PostMapping("/{postId}/like")
    public ApiResponse<PostInteractionVO> toggleLike(@PathVariable String postId) {
        return ApiResponse.success(postService.toggleLike(postId));
    }

    @PostMapping("/{postId}/favorite")
    public ApiResponse<PostInteractionVO> toggleFavorite(@PathVariable String postId) {
        return ApiResponse.success(postService.toggleFavorite(postId));
    }
}
