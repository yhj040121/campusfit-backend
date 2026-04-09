package com.campusfit.modules.post.service;

import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.post.dto.PostCommentCreateRequest;
import com.campusfit.modules.post.dto.PostCreateRequest;
import com.campusfit.modules.post.vo.PostCardVO;
import com.campusfit.modules.post.vo.PostCommentVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import com.campusfit.modules.post.vo.PostDetailVO;
import com.campusfit.modules.post.vo.PostEditVO;
import com.campusfit.modules.post.vo.PostInteractionVO;
import com.campusfit.modules.post.vo.PostProductJumpVO;

import java.util.List;

public interface PostService {

    List<PostCardVO> listRecommendations();

    List<PostCardVO> listMine();

    List<PostCardVO> listLiked();
    List<PostCardVO> listFavorites();

    List<PostCardVO> search(String keyword, String scene, String style, String budget);

    PostDetailVO getDetail(String postId);

    PostProductJumpVO getProductJumpInfo(String postId);

    PostProductJumpVO trackProductJump(String postId);

    PostEditVO getMineForEdit(String postId);

    PostCreateResultVO create(PostCreateRequest request);

    PostCreateResultVO updateMine(String postId, PostCreateRequest request);

    void deleteMine(String postId);

    List<PostCommentVO> listComments(String postId);

    PostCommentVO createComment(String postId, PostCommentCreateRequest request);

    void deleteComment(String postId, String commentId);

    PostInteractionVO toggleCommentLike(String postId, String commentId);

    List<UserCardVO> listLikeUsers(String postId);

    PostInteractionVO toggleLike(String postId);

    PostInteractionVO toggleFavorite(String postId);
}

