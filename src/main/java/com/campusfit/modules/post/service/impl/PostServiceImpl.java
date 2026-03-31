package com.campusfit.modules.post.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.activity.service.ActivityService;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.cooperation.service.CooperationService;
import com.campusfit.modules.post.dto.PostCommentCreateRequest;
import com.campusfit.modules.post.dto.PostCreateRequest;
import com.campusfit.modules.post.service.PostService;
import com.campusfit.modules.post.vo.PostCardVO;
import com.campusfit.modules.post.vo.PostCommentVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import com.campusfit.modules.post.vo.PostDetailVO;
import com.campusfit.modules.post.vo.PostEditVO;
import com.campusfit.modules.post.vo.PostInteractionVO;
import com.campusfit.modules.post.vo.PostProductJumpVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class PostServiceImpl implements PostService {

    private static final String DEFAULT_INCENTIVE_TIP = "商家推广费会按平台规则拆分为服务费和激励池，创作者激励主要参考互动、质量与合规表现。";
    private static final String DEFAULT_GUIDE_TIP = "请结合预算、使用频率和场景需求理性选购。";
    private static final String DEFAULT_CLICK_TIP = "本次跳转会记录为导购点击，并与点赞、评论、收藏一起影响内容传播分析和后续创作激励。";
    private static final DateTimeFormatter PUBLISH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
    private static final int COOPERATION_STATUS_PENDING = 0;
    private static final int COOPERATION_STATUS_RUNNING = 1;
    private static final int COOPERATION_STATUS_REWARD_READY = 2;
    private static final int COOPERATION_STATUS_REWARD_ISSUED = 3;
    private static final int COOPERATION_STATUS_ABANDONED = 4;
    private static final int COOPERATION_DELETE_PROTECTION_DAYS = 30;

    private static final String CARD_SELECT = """
        select
            p.id,
            p.post_code,
            p.user_id,
            p.title,
            p.subtitle,
            p.description,
            p.cover_tag,
            p.cover_image_url,
            p.status,
            p.audit_status,
            p.cooperation_id,
            p.scene_tag,
            p.style_tag,
            p.budget_tag,
            p.like_count,
            p.comment_count,
            p.favorite_count,
            p.share_count,
            u.nickname,
            u.avatar_url,
            coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
            coalesce(up.avatar_class, '') as avatar_class,
            up.school_name,
            up.grade_name,
            up.signature,
            cc.cooperation_title,
            cc.cooperation_status,
            cc.reward_issued_at,
            cc.abandoned_at,
            pl.product_name,
            pl.platform_name,
            pl.price_amount,
            pl.profit_label,
            pl.guide_tip
        from post p
        join app_user u on u.id = p.user_id
        left join user_profile up on up.user_id = u.id
        left join creator_cooperation cc on cc.id = p.cooperation_id
        left join product_link pl on pl.post_id = p.id and pl.link_status = 1
            and nullif(trim(pl.product_url), '') is not null
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ActivityService activityService;
    private final CooperationService cooperationService;

    public PostServiceImpl(JdbcTemplate jdbcTemplate, ActivityService activityService, CooperationService cooperationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.activityService = activityService;
        this.cooperationService = cooperationService;
    }

    @Override
    public List<PostCardVO> listRecommendations() {
        String sql = CARD_SELECT + " where p.status = 1 and p.audit_status = 1 order by p.created_at desc, p.id desc";
        return jdbcTemplate.query(sql, this::mapPostCard);
    }

    @Override
    public List<PostCardVO> listMine() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = CARD_SELECT + " where p.user_id = ? and p.audit_status in (0, 1, 2) and p.status in (0, 1) order by p.updated_at desc, p.id desc";
        return jdbcTemplate.query(sql, this::mapPostCard, currentUserId);
    }

    @Override
    public List<PostCardVO> listLiked() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = CARD_SELECT + """
             join post_like pkl on pkl.post_id = p.id and pkl.user_id = ?
             where p.status = 1 and p.audit_status = 1
             order by pkl.created_at desc, pkl.id desc
            """;
        return jdbcTemplate.query(sql, this::mapPostCard, currentUserId);
    }

    @Override
    public List<PostCardVO> listFavorites() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = CARD_SELECT + """
             join post_favorite pf on pf.post_id = p.id and pf.user_id = ?
             where p.status = 1 and p.audit_status = 1
             order by pf.created_at desc, pf.id desc
            """;
        return jdbcTemplate.query(sql, this::mapPostCard, currentUserId);
    }

    @Override
    public List<PostCardVO> search(String keyword, String scene, String style, String budget) {
        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasScene = scene != null && !scene.isBlank();
        boolean hasStyle = style != null && !style.isBlank();
        boolean hasBudget = budget != null && !budget.isBlank();
        if (!hasKeyword && !hasScene && !hasStyle && !hasBudget) {
            return listRecommendations();
        }
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder(CARD_SELECT);
        sql.append(" where p.status = 1 and p.audit_status = 1");

        if (hasKeyword) {
            String likeKeyword = "%" + keyword.trim() + "%";
            sql.append("""
                 and (
                      p.title like ?
                   or p.subtitle like ?
                   or p.description like ?
                   or p.scene_tag like ?
                   or p.style_tag like ?
                   or p.budget_tag like ?
                   or u.nickname like ?
                   or coalesce(pl.product_name, '') like ?
                 )
                """);
            for (int i = 0; i < 8; i += 1) {
                params.add(likeKeyword);
            }
        }
        if (hasScene) {
            sql.append(" and p.scene_tag = ?");
            params.add(scene.trim());
        }
        if (hasStyle) {
            sql.append(" and p.style_tag = ?");
            params.add(style.trim());
        }
        if (hasBudget) {
            sql.append(" and p.budget_tag = ?");
            params.add(budget.trim());
        }
        sql.append(" order by p.created_at desc, p.id desc");
        return jdbcTemplate.query(sql.toString(), this::mapPostCard, params.toArray());
    }

    @Override
    public PostDetailVO getDetail(String postId) {
        Long currentUserId = UserAuthContext.getCurrentUserId();
        long viewerUserId = currentUserId == null ? -1L : currentUserId;
        String sql = """
            select
                p.id,
                p.post_code,
                p.user_id,
                p.title,
                p.subtitle,
                p.description,
                p.created_at,
                p.cover_tag,
                p.cover_image_url,
                p.scene_tag,
                p.style_tag,
                p.budget_tag,
                p.like_count,
                p.comment_count,
                p.favorite_count,
                p.share_count,
                u.nickname,
                u.avatar_url,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class,
                up.school_name,
                up.grade_name,
                pl.product_url,
                pl.product_name,
                pl.platform_name,
                pl.price_amount,
                pl.profit_label,
                pl.guide_tip,
                exists(select 1 from post_like x where x.post_id = p.id and x.user_id = ?) as liked,
                exists(select 1 from post_favorite x where x.post_id = p.id and x.user_id = ?) as favorited,
                case
                    when p.user_id = ? then true
                    else exists(select 1 from user_follow x where x.follower_user_id = ? and x.followee_user_id = p.user_id)
                end as followed
            from post p
            join app_user u on u.id = p.user_id
            left join user_profile up on up.user_id = u.id
            left join product_link pl on pl.post_id = p.id and pl.link_status = 1
                and nullif(trim(pl.product_url), '') is not null
            where p.status = 1 and p.audit_status = 1 and p.post_code = ?
            """;
        List<PostDetailVO> list = jdbcTemplate.query(sql, (rs, rowNum) -> new PostDetailVO(
            rs.getString("post_code"),
            coalesce(rs.getString("cover_tag"), "校园精选"),
            rs.getString("title"),
            coalesce(rs.getString("subtitle"), buildSubtitle(rs.getString("description"))),
            formatPublishTime(rs.getTimestamp("created_at")),
            coalesce(rs.getString("description"), "暂时还没有内容介绍。"),
            coalesce(rs.getString("cover_image_url"), ""),
            findImageUrls(rs.getLong("id")),
            rs.getLong("user_id"),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_url"), ""),
            coalesce(rs.getString("avatar_class"), ""),
            joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
            currentUserId != null && rs.getLong("user_id") == currentUserId,
            rs.getBoolean("liked"),
            rs.getBoolean("favorited"),
            rs.getBoolean("followed"),
            coalesce(rs.getString("scene_tag"), "校园"),
            coalesce(rs.getString("style_tag"), "极简"),
            coalesce(rs.getString("budget_tag"), ""),
            rs.getInt("like_count"),
            rs.getInt("comment_count"),
            rs.getInt("favorite_count"),
            rs.getInt("share_count"),
            formatPrice(rs.getBigDecimal("price_amount")),
            coalesce(rs.getString("product_name"), rs.getString("title")),
            coalesce(rs.getString("platform_name"), "外部平台"),
            coalesce(rs.getString("product_url"), ""),
            coalesce(rs.getString("profit_label"), DEFAULT_INCENTIVE_TIP),
            coalesce(rs.getString("guide_tip"), DEFAULT_GUIDE_TIP),
            activityService.findByPostCode(rs.getString("post_code")),
            findHighlights(rs.getString("post_code")),
            findCommentPreview(rs.getLong("id"), viewerUserId)
        ), viewerUserId, viewerUserId, viewerUserId, viewerUserId, postId);
        if (list.isEmpty()) {
            throw new BusinessException("未找到对应的穿搭内容");
        }
        return list.get(0);
    }

    @Override
    public PostProductJumpVO getProductJumpInfo(String postId) {
        ProductJumpMeta meta = resolveProductJumpMeta(postId);
        return buildProductJumpVO(meta, fetchProductClickCount(meta.postId()));
    }

    @Override
    @Transactional
    public PostProductJumpVO trackProductJump(String postId) {
        ProductJumpMeta meta = resolveProductJumpMeta(postId);
        Long currentUserId = UserAuthContext.getCurrentUserId();
        if (currentUserId != null && hasRecentTrackedJump(meta.postId(), currentUserId)) {
            return buildProductJumpVO(meta, fetchProductClickCount(meta.postId()));
        }
        jdbcTemplate.update(
            """
            insert into product_link_click (
                post_id, product_link_id, click_user_id, source_page, source_action,
                target_url, platform_name, created_at
            ) values (?, ?, ?, ?, ?, ?, ?, now())
            """,
            meta.postId(),
            meta.productLinkId(),
            currentUserId,
            "product_jump",
            "jump_out",
            meta.productUrl(),
            meta.platformName()
        );
        return buildProductJumpVO(meta, fetchProductClickCount(meta.postId()));
    }

    @Override
    @Transactional
    public PostCreateResultVO create(PostCreateRequest request) {
        PostCreateRequest normalizedRequest = normalizeCreateRequest(request);
        long currentUserId = UserAuthContext.requireUserId();
        Set<String> sceneOptions = loadTagValues("scene");
        Set<String> styleOptions = loadTagValues("style");
        Set<String> budgetOptions = loadTagValues("budget");

        String sceneTag = pickTag(normalizedRequest.tags(), sceneOptions, "\u6821\u56ed");
        String styleTag = pickTag(normalizedRequest.tags(), styleOptions, "\u6781\u7b80");
        String budgetTag = pickTag(normalizedRequest.tags(), budgetOptions, null);
        String subtitle = buildSubtitle(normalizedRequest.desc());
        String coverTag = sceneTag + "\u7a7f\u642d";
        String coverImageUrl = normalizedRequest.imageUrls().isEmpty() ? null : normalizedRequest.imageUrls().get(0);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into post (
                    user_id, title, subtitle, description, scene_tag, style_tag, budget_tag,
                    cover_tag, cover_image_url, status, audit_status, like_count, comment_count,
                    favorite_count, share_count, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 0, 0, 0, 0, now(), now())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, currentUserId);
            statement.setString(2, normalizedRequest.title());
            statement.setString(3, subtitle);
            statement.setString(4, normalizedRequest.desc());
            statement.setString(5, sceneTag);
            statement.setString(6, styleTag);
            statement.setString(7, budgetTag);
            statement.setString(8, coverTag);
            statement.setString(9, coverImageUrl);
            return statement;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new BusinessException("穿搭发布失败，请稍后再试");
        }
        long postId = generatedKey.longValue();
        String postCode = "look" + postId;
        jdbcTemplate.update("update post set post_code = ? where id = ?", postCode, postId);

        syncPostImages(postId, normalizedRequest.imageUrls());
        syncPostTags(postId, normalizedRequest.tags(), sceneOptions, styleOptions, budgetOptions);
        upsertProductLink(postId, normalizedRequest.title(), normalizedRequest.productLink(), normalizedRequest.productPrice());
        activityService.bindPostToActivity(postId, currentUserId, normalizedRequest.activityId());
        cooperationService.bindPostToCooperation(postId, currentUserId, normalizedRequest.cooperationId());

        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            currentUserId,
            "\u7cfb\u7edf\u901a\u77e5",
            "\u4f60\u7684\u7a7f\u642d\u5df2\u63d0\u4ea4\u5ba1\u6838",
            "\u4f60\u7684\u7a7f\u642d\u300a" + normalizedRequest.title() + "\u300b\u5df2\u63d0\u4ea4\u81f3\u5185\u5bb9\u5ba1\u6838\u961f\u5217\u3002"
        );

        return new PostCreateResultVO(postCode, "PENDING", "已提交审核");
    }

    @Override
    public PostEditVO getMineForEdit(String postId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        if (meta.authorUserId() != currentUserId) {
            throw new BusinessException("只能编辑自己发布的穿搭");
        }
        String sql = """
            select p.post_code, p.title, p.description, coalesce(pl.product_url, '') as product_url,
                   nullif(pl.price_amount, 0) as product_price
            from post p
            left join product_link pl on pl.post_id = p.id and pl.link_status = 1
                and nullif(trim(pl.product_url), '') is not null
            where p.id = ?
            limit 1
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new PostEditVO(
            rs.getString("post_code"),
            rs.getString("title"),
            coalesce(rs.getString("description"), ""),
            findImageUrls(meta.id()),
            findTags(meta.id()),
            coalesce(rs.getString("product_url"), ""),
            rs.getBigDecimal("product_price"),
            activityService.findByPostCode(rs.getString("post_code")),
            cooperationService.findByPostCode(rs.getString("post_code"))
        ), meta.id());
    }

    @Override
    @Transactional
    public PostCreateResultVO updateMine(String postId, PostCreateRequest request) {
        PostCreateRequest normalizedRequest = normalizeCreateRequest(request);
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        if (meta.authorUserId() != currentUserId) {
            throw new BusinessException("只能编辑自己发布的穿搭");
        }

        Set<String> sceneOptions = loadTagValues("scene");
        Set<String> styleOptions = loadTagValues("style");
        Set<String> budgetOptions = loadTagValues("budget");

        String sceneTag = pickTag(normalizedRequest.tags(), sceneOptions, "\u6821\u56ed");
        String styleTag = pickTag(normalizedRequest.tags(), styleOptions, "\u6781\u7b80");
        String budgetTag = pickTag(normalizedRequest.tags(), budgetOptions, null);
        String subtitle = buildSubtitle(normalizedRequest.desc());
        String coverTag = sceneTag + "\u7a7f\u642d";
        String coverImageUrl = normalizedRequest.imageUrls().isEmpty() ? null : normalizedRequest.imageUrls().get(0);

        jdbcTemplate.update(
            """
            update post
            set title = ?, subtitle = ?, description = ?, scene_tag = ?, style_tag = ?, budget_tag = ?,
                cover_tag = ?, cover_image_url = ?, status = 1, audit_status = 0, updated_at = now()
            where id = ?
            """,
            normalizedRequest.title(),
            subtitle,
            normalizedRequest.desc(),
            sceneTag,
            styleTag,
            budgetTag,
            coverTag,
            coverImageUrl,
            meta.id()
        );

        syncPostImages(meta.id(), normalizedRequest.imageUrls());
        syncPostTags(meta.id(), normalizedRequest.tags(), sceneOptions, styleOptions, budgetOptions);
        upsertProductLink(meta.id(), normalizedRequest.title(), normalizedRequest.productLink(), normalizedRequest.productPrice());
        activityService.bindPostToActivity(meta.id(), currentUserId, normalizedRequest.activityId());
        cooperationService.bindPostToCooperation(meta.id(), currentUserId, normalizedRequest.cooperationId());

        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            currentUserId,
            "\u7cfb\u7edf\u901a\u77e5",
            "\u4f60\u7684\u7a7f\u642d\u5df2\u91cd\u65b0\u63d0\u4ea4\u5ba1\u6838",
            "\u4f60\u7684\u7a7f\u642d\u300a" + normalizedRequest.title() + "\u300b\u5df2\u91cd\u65b0\u63d0\u4ea4\u5ba1\u6838\uff0c\u5f85\u5ba1\u6838\u901a\u8fc7\u540e\u518d\u5bf9\u5916\u5c55\u793a\u3002"
        );

        return new PostCreateResultVO(postId, "PENDING", "修改已提交审核");
    }

    @Override
    @Transactional
    public void deleteMine(String postId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        if (meta.authorUserId() != currentUserId) {
            throw new BusinessException("只能删除自己发布的穿搭内容");
        }
        DeletePolicy deletePolicy = resolveDeletePolicy(
            meta.cooperationId(),
            meta.cooperationTitle(),
            meta.cooperationStatus(),
            meta.rewardIssuedAt(),
            meta.abandonedAt(),
            meta.title()
        );
        if (!deletePolicy.canDelete()) {
            throw new BusinessException(deletePolicy.blockedReason());
        }
        Long cooperationId = meta.cooperationId();
        jdbcTemplate.update("delete from product_link_click where post_id = ?", meta.id());
        if (tableExists("commission_record")) {
            jdbcTemplate.update("delete from commission_record where post_id = ?", meta.id());
        }
        jdbcTemplate.update("delete from post_activity_binding where post_id = ?", meta.id());
        jdbcTemplate.update(
            "delete pcl from post_comment_like pcl join post_comment pc on pc.id = pcl.comment_id where pc.post_id = ?",
            meta.id()
        );
        jdbcTemplate.update("delete from post_comment where post_id = ?", meta.id());
        jdbcTemplate.update("delete from post_like where post_id = ?", meta.id());
        jdbcTemplate.update("delete from post_favorite where post_id = ?", meta.id());
        jdbcTemplate.update("delete from post_image where post_id = ?", meta.id());
        jdbcTemplate.update("delete from post_tag where post_id = ?", meta.id());
        jdbcTemplate.update("delete from product_link where post_id = ?", meta.id());
        jdbcTemplate.update("delete from post where id = ?", meta.id());
        cooperationService.syncProgressByCooperationId(cooperationId);
    }

    @Override
    @Transactional
    public void shelfDownMine(String postId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        if (meta.authorUserId() != currentUserId) {
            throw new BusinessException("只能下架自己发布的穿搭内容");
        }
        if (meta.auditStatus() != 1) {
            throw new BusinessException("当前内容未通过审核，暂时不能下架");
        }
        if (meta.status() == 0) {
            throw new BusinessException("当前内容已处于下架状态");
        }
        jdbcTemplate.update("update post set status = 0, updated_at = now() where id = ?", meta.id());
        jdbcTemplate.update("update product_link set link_status = 0 where post_id = ?", meta.id());
        cooperationService.syncProgressByCooperationId(meta.cooperationId());
    }

    @Override
    @Transactional
    public void restoreMine(String postId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        if (meta.authorUserId() != currentUserId) {
            throw new BusinessException("只能操作自己发布的穿搭内容");
        }
        if (meta.auditStatus() != 1) {
            throw new BusinessException("审核中的内容或已驳回内容暂时不能重新上架");
        }
        if (meta.status() == 1) {
            throw new BusinessException("当前内容已是上架状态");
        }
        jdbcTemplate.update("update post set status = 1, updated_at = now() where id = ?", meta.id());
        jdbcTemplate.update("update product_link set link_status = 1 where post_id = ?", meta.id());
        cooperationService.syncProgressByCooperationId(meta.cooperationId());
    }

    @Override
    public List<PostCommentVO> listComments(String postId) {
        Long currentUserId = UserAuthContext.getCurrentUserId();
        long viewerUserId = currentUserId == null ? -1L : currentUserId;
        PostMeta meta = resolvePostMeta(postId);
        return fetchCommentThreads(meta.id(), viewerUserId);
    }

    @Override
    @Transactional
    public PostCommentVO createComment(String postId, PostCommentCreateRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        ReplyTarget replyTarget = resolveReplyTarget(meta.id(), request.replyToCommentId());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into post_comment (
                    post_id, user_id, content, like_count, status, created_at, parent_comment_id, reply_user_id
                ) values (?, ?, ?, 0, 1, now(), ?, ?)
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, meta.id());
            statement.setLong(2, currentUserId);
            statement.setString(3, request.content());
            if (replyTarget.parentCommentId() == null) {
                statement.setNull(4, java.sql.Types.BIGINT);
            } else {
                statement.setLong(4, replyTarget.parentCommentId());
            }
            if (replyTarget.replyUserId() == null) {
                statement.setNull(5, java.sql.Types.BIGINT);
            } else {
                statement.setLong(5, replyTarget.replyUserId());
            }
            return statement;
        }, keyHolder);
        jdbcTemplate.update("update post set comment_count = comment_count + 1 where id = ?", meta.id());
        if (meta.authorUserId() != currentUserId) {
            jdbcTemplate.update(
                "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
                meta.authorUserId(),
                "评论通知",
                "你收到了新的评论",
                "有人评论了你的穿搭《" + meta.title() + "》。"
            );
        }
        Long commentId = keyHolder.getKey() == null ? null : keyHolder.getKey().longValue();
        return buildCurrentUserComment(commentId, request.content(), currentUserId, replyTarget);
    }

    @Override
    @Transactional
    public void deleteComment(String postId, String commentId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        long targetCommentId = parseCommentId(commentId);
        Long ownerUserId = jdbcTemplate.query(
            """
            select user_id
            from post_comment
            where id = ? and post_id = ? and status = 1
            """,
            rs -> rs.next() ? rs.getLong("user_id") : null,
            targetCommentId,
            meta.id()
        );
        if (ownerUserId == null) {
            throw new BusinessException("评论不存在或已被删除");
        }
        if (ownerUserId != currentUserId) {
            throw new BusinessException("只能删除自己发表的评论");
        }
        int affected = jdbcTemplate.update(
            "update post_comment set status = 0 where id = ? and post_id = ? and user_id = ? and status = 1",
            targetCommentId,
            meta.id(),
            currentUserId
        );
        if (affected <= 0) {
            throw new BusinessException("评论删除失败");
        }
        jdbcTemplate.update(
            "update post set comment_count = case when comment_count > 0 then comment_count - 1 else 0 end where id = ?",
            meta.id()
        );
    }

    @Override
    @Transactional
    public PostInteractionVO toggleCommentLike(String postId, String commentId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        long targetCommentId = parseCommentId(commentId);
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from post_comment where id = ? and post_id = ? and status = 1",
            Integer.class,
            targetCommentId,
            meta.id()
        );
        if (exists == null || exists <= 0) {
            throw new BusinessException("璇勮涓嶅瓨鍦ㄦ垨宸茶鍒犻櫎");
        }

        Integer liked = jdbcTemplate.queryForObject(
            "select count(*) from post_comment_like where comment_id = ? and user_id = ?",
            Integer.class,
            targetCommentId,
            currentUserId
        );
        if (liked != null && liked > 0) {
            jdbcTemplate.update("delete from post_comment_like where comment_id = ? and user_id = ?", targetCommentId, currentUserId);
            jdbcTemplate.update(
                "update post_comment set like_count = case when like_count > 0 then like_count - 1 else 0 end where id = ?",
                targetCommentId
            );
            return new PostInteractionVO(false, fetchCommentLikeCount(targetCommentId));
        }

        jdbcTemplate.update(
            "insert into post_comment_like (comment_id, user_id, created_at) values (?, ?, now())",
            targetCommentId,
            currentUserId
        );
        jdbcTemplate.update("update post_comment set like_count = like_count + 1 where id = ?", targetCommentId);
        return new PostInteractionVO(true, fetchCommentLikeCount(targetCommentId));
    }

    @Override
    public List<UserCardVO> listLikeUsers(String postId) {
        Long currentUserId = UserAuthContext.getCurrentUserId();
        long viewerUserId = currentUserId == null ? -1L : currentUserId;
        PostMeta meta = resolvePostMeta(postId);
        String sql = """
            select
                u.id,
                u.nickname,
                u.avatar_url,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class,
                up.school_name,
                up.grade_name,
                up.signature,
                case
                    when u.id = ? then true
                    else exists(
                        select 1
                        from user_follow uf
                        where uf.follower_user_id = ?
                          and uf.followee_user_id = u.id
                    )
                end as active
            from post_like pl
            join app_user u on u.id = pl.user_id
            left join user_profile up on up.user_id = u.id
            where pl.post_id = ?
            order by pl.created_at desc, pl.id desc
            """;
        return jdbcTemplate.query(sql, this::mapUserCard, viewerUserId, viewerUserId, meta.id());
    }

    @Override
    @Transactional
    public PostInteractionVO toggleLike(String postId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from post_like where post_id = ? and user_id = ?",
            Integer.class,
            meta.id(),
            currentUserId
        );
        boolean active = exists != null && exists > 0;
        if (active) {
            jdbcTemplate.update("delete from post_like where post_id = ? and user_id = ?", meta.id(), currentUserId);
            jdbcTemplate.update("update post set like_count = case when like_count > 0 then like_count - 1 else 0 end where id = ?", meta.id());
            cooperationService.syncProgressByCooperationId(meta.cooperationId());
            return new PostInteractionVO(false, fetchCount(meta.id(), "like_count"));
        }
        jdbcTemplate.update("insert into post_like (post_id, user_id, created_at) values (?, ?, now())", meta.id(), currentUserId);
        jdbcTemplate.update("update post set like_count = like_count + 1 where id = ?", meta.id());
        cooperationService.syncProgressByCooperationId(meta.cooperationId());
        if (meta.authorUserId() != currentUserId) {
            jdbcTemplate.update(
                "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
                meta.authorUserId(),
                "互动通知",
                "有人点赞了你的穿搭",
                "你的穿搭《" + meta.title() + "》刚刚收到了新的点赞。"
            );
        }
        return new PostInteractionVO(true, fetchCount(meta.id(), "like_count"));
    }

    @Override
    @Transactional
    public PostInteractionVO toggleFavorite(String postId) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from post_favorite where post_id = ? and user_id = ?",
            Integer.class,
            meta.id(),
            currentUserId
        );
        boolean active = exists != null && exists > 0;
        if (active) {
            jdbcTemplate.update("delete from post_favorite where post_id = ? and user_id = ?", meta.id(), currentUserId);
            jdbcTemplate.update("update post set favorite_count = case when favorite_count > 0 then favorite_count - 1 else 0 end where id = ?", meta.id());
            return new PostInteractionVO(false, fetchCount(meta.id(), "favorite_count"));
        }
        jdbcTemplate.update("insert into post_favorite (post_id, user_id, created_at) values (?, ?, now())", meta.id(), currentUserId);
        jdbcTemplate.update("update post set favorite_count = favorite_count + 1 where id = ?", meta.id());
        return new PostInteractionVO(true, fetchCount(meta.id(), "favorite_count"));
    }

    private PostCardVO mapPostCard(ResultSet rs, int rowNum) throws SQLException {
        String publishStatus = resolvePublishStatus(rs.getInt("status"), rs.getInt("audit_status"));
        DeletePolicy deletePolicy = resolveDeletePolicy(
            (Long) rs.getObject("cooperation_id"),
            rs.getString("cooperation_title"),
            (Integer) rs.getObject("cooperation_status"),
            rs.getTimestamp("reward_issued_at"),
            rs.getTimestamp("abandoned_at"),
            rs.getString("title")
        );
        return new PostCardVO(
            rs.getString("post_code"),
            rs.getLong("user_id"),
            coalesce(rs.getString("cover_tag"), "\u6821\u56ed\u7cbe\u9009"),
            rs.getString("title"),
            coalesce(rs.getString("subtitle"), buildSubtitle(rs.getString("description"))),
            coalesce(rs.getString("description"), "\u6682\u65f6\u8fd8\u6ca1\u6709\u5185\u5bb9\u4ecb\u7ecd\u3002"),
            coalesce(rs.getString("cover_image_url"), ""),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_url"), ""),
            coalesce(rs.getString("avatar_class"), ""),
            joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
            coalesce(rs.getString("scene_tag"), "\u6821\u56ed"),
            coalesce(rs.getString("style_tag"), "\u6781\u7b80"),
            coalesce(rs.getString("budget_tag"), ""),
            rs.getInt("like_count"),
            rs.getInt("comment_count"),
            rs.getInt("favorite_count"),
            rs.getInt("share_count"),
            formatPrice(rs.getBigDecimal("price_amount")),
            coalesce(rs.getString("product_name"), rs.getString("title")),
            coalesce(rs.getString("platform_name"), "\u5916\u90e8\u5e73\u53f0"),
            coalesce(rs.getString("profit_label"), DEFAULT_INCENTIVE_TIP),
            coalesce(rs.getString("guide_tip"), DEFAULT_GUIDE_TIP),
            publishStatus,
            resolvePublishStatusText(publishStatus),
            resolvePublishStatusDesc(publishStatus),
            "PUBLISHED".equals(publishStatus),
            "PUBLISHED".equals(publishStatus),
            "OFFLINE".equals(publishStatus),
            deletePolicy.canDelete(),
            coalesce(deletePolicy.blockedReason(), "")
        );
    }

    private String resolvePublishStatus(int status, int auditStatus) {
        if (auditStatus == 2) {
            return "REJECTED";
        }
        if (auditStatus == 0) {
            return "PENDING";
        }
        if (status == 0) {
            return "OFFLINE";
        }
        return "PUBLISHED";
    }

    private String resolvePublishStatusText(String publishStatus) {
        return switch (publishStatus) {
            case "PENDING" -> "\u5ba1\u6838\u4e2d";
            case "REJECTED" -> "\u5ba1\u6838\u9a73\u56de";
            case "OFFLINE" -> "\u5df2\u4e0b\u67b6";
            default -> "\u5df2\u53d1\u5e03";
        };
    }

    private String resolvePublishStatusDesc(String publishStatus) {
        return switch (publishStatus) {
            case "PENDING" -> "\u5185\u5bb9\u6b63\u5728\u5ba1\u6838\uff0c\u6682\u65f6\u4e0d\u4f1a\u5c55\u793a\u5728\u9996\u9875\u4fe1\u606f\u6d41\u3002";
            case "REJECTED" -> "\u5185\u5bb9\u672a\u901a\u8fc7\u5ba1\u6838\uff0c\u53ef\u4fee\u6539\u540e\u518d\u6b21\u53d1\u5e03\u3002";
            case "OFFLINE" -> "\u5185\u5bb9\u5df2\u4e0b\u67b6\uff0c\u4ec5\u4f60\u81ea\u5df1\u53ef\u4ee5\u7ee7\u7eed\u7ba1\u7406\u3002";
            default -> "\u5185\u5bb9\u5df2\u901a\u8fc7\u5ba1\u6838\uff0c\u6b63\u5728\u5bf9\u5916\u5c55\u793a\u3002";
        };
    }

    private UserCardVO mapUserCard(ResultSet rs, int rowNum) throws SQLException {
        return new UserCardVO(
            rs.getLong("id"),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_url"), ""),
            coalesce(rs.getString("avatar_class"), ""),
            buildIntro(rs.getString("school_name"), rs.getString("grade_name"), rs.getString("signature")),
            rs.getBoolean("active")
        );
    }

    private List<PostCommentVO> fetchCommentThreads(long postId, long viewerUserId) {
        String sql = """
            select
                c.id,
                c.parent_comment_id,
                c.user_id,
                c.content,
                c.like_count,
                c.created_at,
                c.reply_user_id,
                u.nickname,
                u.avatar_url,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class,
                coalesce(ru.nickname, '') as reply_to_name,
                exists(select 1 from post_comment_like pcl where pcl.comment_id = c.id and pcl.user_id = ?) as liked
            from post_comment c
            join app_user u on u.id = c.user_id
            left join user_profile up on up.user_id = u.id
            left join app_user ru on ru.id = c.reply_user_id
            where c.post_id = ? and c.status = 1
            order by coalesce(c.parent_comment_id, c.id) desc,
                     case when c.parent_comment_id is null then 0 else 1 end asc,
                     c.created_at asc,
                     c.id asc
            """;
        List<CommentRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapCommentRow(rs, viewerUserId), viewerUserId, postId);
        return buildCommentThreads(rows);
    }

    private List<PostCommentVO> findCommentPreview(long postId, long viewerUserId) {
        String sql = """
            select
                c.id,
                c.parent_comment_id,
                c.user_id,
                c.content,
                c.like_count,
                c.created_at,
                c.reply_user_id,
                u.nickname,
                u.avatar_url,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class,
                coalesce(ru.nickname, '') as reply_to_name,
                exists(select 1 from post_comment_like pcl where pcl.comment_id = c.id and pcl.user_id = ?) as liked
            from post_comment c
            join app_user u on u.id = c.user_id
            left join user_profile up on up.user_id = u.id
            left join app_user ru on ru.id = c.reply_user_id
            where c.post_id = ? and c.status = 1 and c.parent_comment_id is null
            order by c.created_at desc, c.id desc
            limit 3
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> buildCommentVO(mapCommentRow(rs, viewerUserId), Collections.emptyList()), viewerUserId, postId);
    }

    private CommentRow mapCommentRow(ResultSet rs, long viewerUserId) throws SQLException {
        return new CommentRow(
            rs.getLong("id"),
            rs.getObject("parent_comment_id") == null ? null : rs.getLong("parent_comment_id"),
            rs.getLong("user_id"),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_url"), ""),
            coalesce(rs.getString("avatar_class"), ""),
            rs.getString("content"),
            formatRelativeTime(rs.getTimestamp("created_at")),
            rs.getInt("like_count"),
            rs.getBoolean("liked"),
            coalesce(rs.getString("reply_to_name"), ""),
            rs.getLong("user_id") == viewerUserId
        );
    }

    private List<PostCommentVO> buildCommentThreads(List<CommentRow> rows) {
        Map<Long, PostCommentVO> rootMap = new LinkedHashMap<>();
        Map<Long, List<PostCommentVO>> pendingReplies = new LinkedHashMap<>();
        List<PostCommentVO> orphans = new ArrayList<>();
        for (CommentRow row : rows) {
            if (row.parentCommentId() == null) {
                List<PostCommentVO> replies = pendingReplies.remove(row.id());
                rootMap.put(row.id(), buildCommentVO(row, replies == null ? Collections.emptyList() : replies));
                continue;
            }
            PostCommentVO reply = buildCommentVO(row, Collections.emptyList());
            PostCommentVO parent = rootMap.get(row.parentCommentId());
            if (parent == null) {
                pendingReplies.computeIfAbsent(row.parentCommentId(), key -> new ArrayList<>()).add(reply);
                continue;
            }
            List<PostCommentVO> replies = new ArrayList<>(parent.replies());
            replies.add(reply);
            rootMap.put(row.parentCommentId(), copyWithReplies(parent, replies));
        }
        for (List<PostCommentVO> replies : pendingReplies.values()) {
            orphans.addAll(replies);
        }
        List<PostCommentVO> result = new ArrayList<>(rootMap.values());
        result.addAll(orphans);
        return result;
    }

    private PostCommentVO buildCommentVO(CommentRow row, List<PostCommentVO> replies) {
        return new PostCommentVO(
            String.valueOf(row.id()),
            row.parentCommentId() == null ? "" : String.valueOf(row.parentCommentId()),
            row.name(),
            row.avatar(),
            row.avatarUrl(),
            row.avatarClass(),
            row.text(),
            row.time(),
            row.likes(),
            row.mine(),
            row.liked(),
            row.replyToName(),
            replies == null ? Collections.emptyList() : replies
        );
    }

    private PostCommentVO copyWithReplies(PostCommentVO comment, List<PostCommentVO> replies) {
        return new PostCommentVO(
            comment.id(),
            comment.parentId(),
            comment.name(),
            comment.avatar(),
            comment.avatarUrl(),
            comment.avatarClass(),
            comment.text(),
            comment.time(),
            comment.likes(),
            comment.mine(),
            comment.liked(),
            comment.replyToName(),
            replies
        );
    }

    private PostCommentVO buildCurrentUserComment(Long id, String content, long currentUserId, ReplyTarget replyTarget) {
        return jdbcTemplate.queryForObject(
            """
            select u.nickname, u.avatar_url, coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                   coalesce(up.avatar_class, '') as avatar_class
            from app_user u
            left join user_profile up on up.user_id = u.id
            where u.id = ?
            """,
            (rs, rowNum) -> new PostCommentVO(
                String.valueOf(id == null ? 0 : id),
                replyTarget.parentCommentId() == null ? "" : String.valueOf(replyTarget.parentCommentId()),
                rs.getString("nickname"),
                coalesce(rs.getString("avatar_text"), "C"),
                coalesce(rs.getString("avatar_url"), ""),
                coalesce(rs.getString("avatar_class"), "soft"),
                content,
                "\u521a\u521a",
                0,
                true,
                false,
                replyTarget.replyToName(),
                Collections.emptyList()
            ),
            currentUserId
        );
    }

    private ReplyTarget resolveReplyTarget(long postId, String replyToCommentId) {
        String normalizedReplyId = normalizeOptionalText(replyToCommentId);
        if (normalizedReplyId == null) {
            return new ReplyTarget(null, null, "");
        }
        long targetCommentId = parseCommentId(normalizedReplyId);
        ReplyTarget target = jdbcTemplate.query(
            """
            select c.id, c.parent_comment_id, c.user_id, u.nickname
            from post_comment c
            join app_user u on u.id = c.user_id
            where c.id = ? and c.post_id = ? and c.status = 1
            limit 1
            """,
            rs -> rs.next() ? new ReplyTarget(
                rs.getObject("parent_comment_id") == null ? rs.getLong("id") : rs.getLong("parent_comment_id"),
                rs.getLong("user_id"),
                rs.getString("nickname")
            ) : null,
            targetCommentId,
            postId
        );
        if (target == null) {
            throw new BusinessException("璇勮涓嶅瓨鍦ㄦ垨宸茶鍒犻櫎");
        }
        return target;
    }

    private int fetchCommentLikeCount(long commentId) {
        Integer count = jdbcTemplate.queryForObject(
            "select like_count from post_comment where id = ?",
            Integer.class,
            commentId
        );
        return count == null ? 0 : count;
    }

    private long parseCommentId(String commentId) {
        try {
            return Long.parseLong(commentId);
        } catch (NumberFormatException exception) {
            throw new BusinessException("\u8bc4\u8bba\u7f16\u53f7\u65e0\u6548");
        }
    }

    private List<String> findImageUrls(long postId) {
        return jdbcTemplate.queryForList(
            "select image_url from post_image where post_id = ? order by sort_order asc, id asc",
            String.class,
            postId
        );
    }

    private List<String> findTags(long postId) {
        return jdbcTemplate.queryForList(
            "select tag_value from post_tag where post_id = ? order by id asc",
            String.class,
            postId
        );
    }

    private void syncPostImages(long postId, List<String> imageUrls) {
        jdbcTemplate.update("delete from post_image where post_id = ?", postId);
        if (imageUrls == null) {
            return;
        }
        for (int i = 0; i < imageUrls.size(); i += 1) {
            jdbcTemplate.update(
                "insert into post_image (post_id, image_url, sort_order, created_at) values (?, ?, ?, now())",
                postId,
                imageUrls.get(i),
                i + 1
            );
        }
    }

    private void syncPostTags(long postId, List<String> tags, Set<String> sceneOptions, Set<String> styleOptions, Set<String> budgetOptions) {
        jdbcTemplate.update("delete from post_tag where post_id = ?", postId);
        if (tags == null) {
            return;
        }
        for (String tag : new LinkedHashSet<>(tags)) {
            jdbcTemplate.update(
                "insert into post_tag (post_id, tag_type, tag_value, created_at) values (?, ?, ?, now())",
                postId,
                resolveTagType(tag, sceneOptions, styleOptions, budgetOptions),
                tag
            );
        }
    }

    private void upsertProductLink(long postId, String productName, String productLink, BigDecimal productPrice) {
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from product_link where post_id = ?",
            Integer.class,
            postId
        );
        if (productLink == null) {
            if (exists != null && exists > 0) {
                jdbcTemplate.update(
                    """
                    update product_link
                    set product_name = ?, platform_name = ?, product_url = '', link_status = 0,
                        price_amount = 0.00, profit_label = ?, guide_tip = ?, last_checked_at = now()
                    where post_id = ?
                    """,
                    productName,
                    "外部平台",
                    DEFAULT_INCENTIVE_TIP,
                    DEFAULT_GUIDE_TIP,
                    postId
                );
            }
            return;
        }
        BigDecimal safePrice = productPrice == null ? BigDecimal.ZERO : productPrice;
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                """
                update product_link
                set product_name = ?, platform_name = ?, product_url = ?, link_status = 1,
                    price_amount = ?, profit_label = ?, guide_tip = ?, last_checked_at = now()
                where post_id = ?
                """,
                productName,
                detectPlatform(productLink),
                productLink,
                safePrice,
                DEFAULT_INCENTIVE_TIP,
                DEFAULT_GUIDE_TIP,
                postId
            );
            return;
        }
        jdbcTemplate.update(
            """
            insert into product_link (
                post_id, product_name, platform_name, product_url, link_status,
                is_partner_product, commission_rate, price_amount, profit_label, guide_tip, last_checked_at, created_at
            ) values (?, ?, ?, ?, 1, 0, 5.00, ?, ?, ?, now(), now())
            """,
            postId,
            productName,
            detectPlatform(productLink),
            productLink,
            safePrice,
            DEFAULT_INCENTIVE_TIP,
            DEFAULT_GUIDE_TIP
        );
    }

    private ProductJumpMeta resolveProductJumpMeta(String postCode) {
        List<ProductJumpMeta> list = jdbcTemplate.query(
            """
            select
                p.id,
                p.post_code,
                p.title,
                pl.id as product_link_id,
                pl.product_name,
                pl.platform_name,
                pl.product_url,
                pl.price_amount,
                pl.profit_label,
                pl.guide_tip
            from post p
            left join product_link pl on pl.post_id = p.id and pl.link_status = 1
                and nullif(trim(pl.product_url), '') is not null
            where p.status = 1 and p.audit_status = 1 and p.post_code = ?
            limit 1
            """,
            (rs, rowNum) -> new ProductJumpMeta(
                rs.getLong("id"),
                rs.getString("post_code"),
                rs.getLong("product_link_id"),
                coalesce(rs.getString("product_name"), rs.getString("title")),
                coalesce(rs.getString("platform_name"), "外部平台"),
                coalesce(rs.getString("product_url"), ""),
                rs.getBigDecimal("price_amount"),
                coalesce(rs.getString("profit_label"), DEFAULT_INCENTIVE_TIP),
                coalesce(rs.getString("guide_tip"), DEFAULT_GUIDE_TIP)
            ),
            postCode
        );
        if (list.isEmpty()) {
            throw new BusinessException("未找到对应的导购内容");
        }
        ProductJumpMeta meta = list.get(0);
        if (meta.productLinkId() <= 0 || meta.productUrl().isBlank()) {
            throw new BusinessException("当前内容暂未配置可用的导购链接");
        }
        return meta;
    }

    private PostProductJumpVO buildProductJumpVO(ProductJumpMeta meta, int clickCount) {
        return new PostProductJumpVO(
            meta.postCode(),
            meta.productName(),
            meta.platformName(),
            formatPrice(meta.priceAmount()),
            meta.productUrl(),
            meta.incentiveTip(),
            meta.guideTip(),
            DEFAULT_CLICK_TIP,
            clickCount
        );
    }

    private int fetchProductClickCount(long postId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*) from (
                select concat('u:', click_user_id) as viewer_key
                from product_link_click
                where post_id = ? and click_user_id is not null
                group by click_user_id
                union all
                select concat('g:', id) as viewer_key
                from product_link_click
                where post_id = ? and click_user_id is null
            ) t
            """,
            Integer.class,
            postId,
            postId
        );
        return count == null ? 0 : count;
    }

    private boolean hasRecentTrackedJump(long postId, long currentUserId) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
            from product_link_click
            where post_id = ?
              and click_user_id = ?
              and created_at >= date_sub(now(), interval 12 hour)
            """,
            Integer.class,
            postId,
            currentUserId
        );
        return count != null && count > 0;
    }

    private List<String> findHighlights(String postCode) {
        String sql = """
            select pt.tag_value
            from post_tag pt
            join post p on p.id = pt.post_id
            where p.post_code = ? and pt.tag_type = 'highlight'
            order by pt.id
            """;
        return jdbcTemplate.queryForList(sql, String.class, postCode);
    }

    private List<String> findCommentPreview(String postCode) {
        String sql = """
            select c.content
            from post_comment c
            join post p on p.id = c.post_id
            where p.post_code = ? and c.status = 1
            order by c.created_at desc, c.id desc
            limit 3
            """;
        return jdbcTemplate.queryForList(sql, String.class, postCode);
    }

    private Set<String> loadTagValues(String category) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(
            "select option_value from tag_option where category = ? and status = 1 order by sort_order asc, id asc",
            String.class,
            category
        ));
    }

    private boolean hasIssuedCooperationReward(PostMeta meta) {
        if (meta.cooperationId() == null || !tableExists("commission_record")) {
            return false;
        }
        if (columnExists("commission_record", "cooperation_id")) {
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from commission_record where post_id = ? and cooperation_id is not null",
                Integer.class,
                meta.id()
            );
            return count != null && count > 0;
        }
        if (columnExists("commission_record", "income_type")) {
            Integer count = jdbcTemplate.queryForObject(
                "select count(*) from commission_record where post_id = ? and income_type = ?",
                Integer.class,
                meta.id(),
                "合作分成"
            );
            return count != null && count > 0;
        }
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from commission_record where post_id = ?",
            Integer.class,
            meta.id()
        );
        return count != null && count > 0;
    }

    private PostMeta resolvePostMeta(String postCode) {
        List<PostMeta> list = jdbcTemplate.query(
            """
            select
                p.id,
                p.user_id,
                p.title,
                p.status,
                p.audit_status,
                p.cooperation_id,
                cc.cooperation_title,
                cc.cooperation_status,
                cc.reward_issued_at,
                cc.abandoned_at
            from post p
            left join creator_cooperation cc on cc.id = p.cooperation_id
            where p.post_code = ?
              and p.audit_status in (0, 1, 2)
              and p.status in (0, 1)
            """,
            (rs, rowNum) -> new PostMeta(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("title"),
                rs.getInt("status"),
                rs.getInt("audit_status"),
                (Long) rs.getObject("cooperation_id"),
                rs.getString("cooperation_title"),
                (Integer) rs.getObject("cooperation_status"),
                rs.getTimestamp("reward_issued_at"),
                rs.getTimestamp("abandoned_at")
            ),
            postCode
        );
        if (list.isEmpty()) {
            throw new BusinessException("\u672a\u627e\u5230\u5bf9\u5e94\u7684\u7a7f\u642d\u5185\u5bb9");
        }
        return list.get(0);
    }

    private int fetchCount(long postId, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            "select " + columnName + " from post where id = ?",
            Integer.class,
            postId
        );
        return count == null ? 0 : count;
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.tables
            where table_schema = database()
              and table_name = ?
            """,
            Integer.class,
            tableName
        );
        return count != null && count > 0;
    }

    private boolean columnExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = ?
              and column_name = ?
            """,
            Integer.class,
            tableName,
            columnName
        );
        return count != null && count > 0;
    }

    private PostCreateRequest normalizeCreateRequest(PostCreateRequest request) {
        if (request == null) {
            throw new BusinessException("发布内容不能为空");
        }
        String title = normalizeRequiredText(request.title(), "标题不能为空");
        String desc = normalizeRequiredText(request.desc(), "描述不能为空");
        List<String> imageUrls = sanitizeValues(request.imageUrls(), 9, "图片最多 9 张");
        List<String> tags = sanitizeValues(request.tags(), 0, "");
        String productLink = normalizeOptionalText(request.productLink());
        BigDecimal productPrice = normalizeOptionalPrice(request.productPrice());
        String activityId = normalizeOptionalText(request.activityId());
        String cooperationId = normalizeOptionalText(request.cooperationId());
        if (productLink == null) {
            productPrice = null;
        }

        if (imageUrls.isEmpty()) {
            throw new BusinessException("请至少上传 1 张图片");
        }
        if (tags.isEmpty()) {
            throw new BusinessException("请至少选择一个标签");
        }

        return new PostCreateRequest(title, desc, imageUrls, tags, productLink, productPrice, activityId, cooperationId);
    }

    private String pickTag(List<String> tags, Set<String> candidates, String fallback) {
        if (tags != null) {
            for (String tag : tags) {
                if (tag != null && candidates.contains(tag)) {
                    return tag;
                }
            }
        }
        return fallback;
    }

    private List<String> sanitizeValues(List<String> values, int maxSize, String maxSizeMessage) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> normalized = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String value : values) {
            String safeValue = normalizeOptionalText(value);
            if (safeValue != null && unique.add(safeValue)) {
                normalized.add(safeValue);
            }
        }
        if (maxSize > 0 && normalized.size() > maxSize) {
            throw new BusinessException(maxSizeMessage);
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, String message) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            throw new BusinessException(message);
        }
        return normalized;
    }

    private BigDecimal normalizeOptionalPrice(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0);
        }
        if (normalized.scale() > 2) {
            throw new BusinessException("\u5546\u54c1\u4ef7\u683c\u6700\u591a\u4fdd\u7559 2 \u4f4d\u5c0f\u6570");
        }
        if (normalized.compareTo(new BigDecimal("0.01")) < 0) {
            throw new BusinessException("\u5546\u54c1\u4ef7\u683c\u5fc5\u987b\u5927\u4e8e 0");
        }
        if (normalized.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new BusinessException("\u5546\u54c1\u4ef7\u683c\u8d85\u51fa\u652f\u6301\u8303\u56f4");
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveTagType(String tag, Set<String> sceneOptions, Set<String> styleOptions, Set<String> budgetOptions) {
        if (sceneOptions.contains(tag)) {
            return "scene";
        }
        if (styleOptions.contains(tag)) {
            return "style";
        }
        if (budgetOptions.contains(tag)) {
            return "budget";
        }
        return "highlight";
    }

    private String buildSubtitle(String desc) {
        if (desc == null || desc.isBlank()) {
            return "一条新的穿搭笔记正在等你查看。";
        }
        String normalized = desc.replace("\r", "").replace("\n", " ").trim();
        return normalized.length() > 40 ? normalized.substring(0, 40) + "..." : normalized;
    }

    private String detectPlatform(String productLink) {
        String normalized = productLink == null ? "" : productLink.toLowerCase(Locale.ROOT);
        if (normalized.contains("taobao") || normalized.contains("tmall")) {
            return "淘宝 / 天猫";
        }
        if (normalized.contains("jd")) {
            return "京东";
        }
        if (normalized.contains("pinduoduo")) {
            return "拼多多";
        }
        if (normalized.contains("weidian")) {
            return "微店";
        }
        return "外部平台";
    }

    private String formatPrice(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return "￥" + amount.stripTrailingZeros().toPlainString();
    }

    private String formatPublishTime(Timestamp createdAt) {
        if (createdAt == null) {
            return "";
        }
        return createdAt.toLocalDateTime().format(PUBLISH_TIME_FORMATTER);
    }

    private DeletePolicy resolveDeletePolicy(
        Long cooperationId,
        String cooperationTitle,
        Integer cooperationStatus,
        Timestamp rewardIssuedAt,
        Timestamp abandonedAt,
        String postTitle
    ) {
        if (cooperationId == null) {
            return DeletePolicy.allow();
        }

        String normalizedPostTitle = coalesce(postTitle, "未命名作品");
        String normalizedCooperationTitle = coalesce(cooperationTitle, "当前合作单");

        if (abandonedAt != null || (cooperationStatus != null && cooperationStatus == COOPERATION_STATUS_ABANDONED)) {
            return DeletePolicy.allow();
        }

        if (rewardIssuedAt != null || (cooperationStatus != null && cooperationStatus == COOPERATION_STATUS_REWARD_ISSUED)) {
            if (rewardIssuedAt == null) {
                return DeletePolicy.block(
                    "作品《" + normalizedPostTitle + "》已参与合作单《" + normalizedCooperationTitle
                        + "》，奖励发放时间尚未同步，暂不能删除。"
                );
            }

            LocalDateTime deleteAvailableAt = rewardIssuedAt.toLocalDateTime().plusDays(COOPERATION_DELETE_PROTECTION_DAYS);
            if (!LocalDateTime.now().isBefore(deleteAvailableAt)) {
                return DeletePolicy.allow();
            }

            return DeletePolicy.block(
                "作品《" + normalizedPostTitle + "》已参与合作单《" + normalizedCooperationTitle
                    + "》，奖励已于 " + formatPublishTime(rewardIssuedAt)
                    + " 发放，需在 " + deleteAvailableAt.format(PUBLISH_TIME_FORMATTER)
                    + " 后才能删除。"
            );
        }

        return DeletePolicy.block(
            buildCooperationDeleteBlockedReason(normalizedPostTitle, normalizedCooperationTitle, cooperationStatus)
        );
    }

    private String buildCooperationDeleteBlockedReason(String postTitle, String cooperationTitle, Integer cooperationStatus) {
        String prefix = "作品《" + postTitle + "》已参与合作单《" + cooperationTitle + "》，";
        if (cooperationStatus == null) {
            return prefix + "当前合作状态未解除，需在合作单取消后，或奖励发放满30天后才能删除。";
        }
        return switch (cooperationStatus) {
            case COOPERATION_STATUS_PENDING -> prefix + "当前合作邀请尚未取消，暂不能删除。只有合作单取消后，或奖励发放满30天后才能删除。";
            case COOPERATION_STATUS_RUNNING -> prefix + "合作仍在进行中，暂不能删除。只有合作单取消后，或奖励发放满30天后才能删除。";
            case COOPERATION_STATUS_REWARD_READY -> prefix + "奖励尚未发放，暂不能删除。只有合作单取消后，或奖励发放满30天后才能删除。";
            default -> prefix + "当前合作状态未解除，需在合作单取消后，或奖励发放满30天后才能删除。";
        };
    }

    private String formatRelativeTime(Timestamp createdAt) {
        if (createdAt == null) {
            return "刚刚";
        }
        Duration duration = Duration.between(createdAt.toLocalDateTime(), LocalDateTime.now());
        if (duration.isNegative() || duration.toMinutes() <= 0) {
            return "刚刚";
        }
        if (duration.toMinutes() < 60) {
            return duration.toMinutes() + " 分钟前";
        }
        if (duration.toHours() < 24) {
            return duration.toHours() + " 小时前";
        }
        if (duration.toDays() < 7) {
            return duration.toDays() + " 天前";
        }
        return createdAt.toLocalDateTime().toLocalDate().toString();
    }

    private String buildIntro(String schoolName, String gradeName, String signature) {
        String schoolLine = joinSchool(schoolName, gradeName);
        if (signature == null || signature.isBlank()) {
            return schoolLine;
        }
        return schoolLine + " · " + signature;
    }

    private String joinSchool(String schoolName, String gradeName) {
        if (schoolName == null || schoolName.isBlank()) {
            return coalesce(gradeName, "校园用户");
        }
        if (gradeName == null || gradeName.isBlank()) {
            return schoolName;
        }
        return schoolName + " · " + gradeName;
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CommentRow(
        long id,
        Long parentCommentId,
        long userId,
        String name,
        String avatar,
        String avatarUrl,
        String avatarClass,
        String text,
        String time,
        int likes,
        boolean liked,
        String replyToName,
        boolean mine
    ) {
    }

    private record ReplyTarget(Long parentCommentId, Long replyUserId, String replyToName) {
    }

    private record PostMeta(
        long id,
        long authorUserId,
        String title,
        int status,
        int auditStatus,
        Long cooperationId,
        String cooperationTitle,
        Integer cooperationStatus,
        Timestamp rewardIssuedAt,
        Timestamp abandonedAt
    ) {
    }

    private record DeletePolicy(boolean canDelete, String blockedReason) {
        private static DeletePolicy allow() {
            return new DeletePolicy(true, null);
        }

        private static DeletePolicy block(String blockedReason) {
            return new DeletePolicy(false, blockedReason);
        }
    }

    private record ProductJumpMeta(
        long postId,
        String postCode,
        long productLinkId,
        String productName,
        String platformName,
        String productUrl,
        BigDecimal priceAmount,
        String incentiveTip,
        String guideTip
    ) {
    }
}



