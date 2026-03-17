package com.campusfit.modules.post.service;

import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.post.dto.PostCommentCreateRequest;
import com.campusfit.modules.post.dto.PostCreateRequest;
import com.campusfit.modules.post.vo.PostCardVO;
import com.campusfit.modules.post.vo.PostCommentVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import com.campusfit.modules.post.vo.PostDetailVO;
import com.campusfit.modules.post.vo.PostInteractionVO;

import java.util.List;

public interface PostService {

    List<PostCardVO> listRecommendations();

    List<PostCardVO> listMine();

    List<PostCardVO> listFavorites();

    List<PostCardVO> search(String keyword);

    PostDetailVO getDetail(String postId);

    PostCreateResultVO create(PostCreateRequest request);

    List<PostCommentVO> listComments(String postId);

    PostCommentVO createComment(String postId, PostCommentCreateRequest request);

    List<UserCardVO> listLikeUsers(String postId);

    PostInteractionVO toggleLike(String postId);

    PostInteractionVO toggleFavorite(String postId);
}
