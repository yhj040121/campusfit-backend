package com.campusfit.modules.post.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.post.dto.PostCommentCreateRequest;
import com.campusfit.modules.post.dto.PostCreateRequest;
import com.campusfit.modules.post.service.PostService;
import com.campusfit.modules.post.vo.PostCardVO;
import com.campusfit.modules.post.vo.PostCommentVO;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import com.campusfit.modules.post.vo.PostDetailVO;
import com.campusfit.modules.post.vo.PostInteractionVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class PostServiceImpl implements PostService {

    private static final String CARD_SELECT = """
        select
            p.id,
            p.post_code,
            p.user_id,
            p.title,
            p.subtitle,
            p.description,
            p.cover_tag,
            p.scene_tag,
            p.style_tag,
            p.budget_tag,
            p.like_count,
            p.comment_count,
            p.favorite_count,
            p.share_count,
            u.nickname,
            coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
            coalesce(up.avatar_class, '') as avatar_class,
            up.school_name,
            up.grade_name,
            up.signature,
            pl.product_name,
            pl.platform_name,
            pl.price_amount,
            pl.profit_label,
            pl.guide_tip
        from post p
        join app_user u on u.id = p.user_id
        left join user_profile up on up.user_id = u.id
        left join product_link pl on pl.post_id = p.id and pl.link_status = 1
        """;

    private final JdbcTemplate jdbcTemplate;

    public PostServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PostCardVO> listRecommendations() {
        String sql = CARD_SELECT + " where p.status = 1 and p.audit_status = 1 order by p.created_at desc, p.id desc";
        return jdbcTemplate.query(sql, this::mapPostCard);
    }

    @Override
    public List<PostCardVO> listMine() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = CARD_SELECT + " where p.status = 1 and p.audit_status = 1 and p.user_id = ? order by p.created_at desc, p.id desc";
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
    public List<PostCardVO> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listRecommendations();
        }
        String likeKeyword = "%" + keyword.trim() + "%";
        String sql = CARD_SELECT + """
             where p.status = 1
               and p.audit_status = 1
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
             order by p.created_at desc, p.id desc
            """;
        return jdbcTemplate.query(
            sql,
            this::mapPostCard,
            likeKeyword, likeKeyword, likeKeyword, likeKeyword,
            likeKeyword, likeKeyword, likeKeyword, likeKeyword
        );
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
                p.cover_tag,
                p.scene_tag,
                p.style_tag,
                p.budget_tag,
                p.like_count,
                p.comment_count,
                p.favorite_count,
                p.share_count,
                u.nickname,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class,
                up.school_name,
                up.grade_name,
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
            where p.status = 1 and p.audit_status = 1 and p.post_code = ?
            """;
        List<PostDetailVO> list = jdbcTemplate.query(sql, (rs, rowNum) -> new PostDetailVO(
            rs.getString("post_code"),
            coalesce(rs.getString("cover_tag"), "校园精选"),
            rs.getString("title"),
            coalesce(rs.getString("subtitle"), buildSubtitle(rs.getString("description"))),
            coalesce(rs.getString("description"), "暂时还没有内容介绍。"),
            rs.getLong("user_id"),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_class"), ""),
            joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
            currentUserId != null && rs.getLong("user_id") == currentUserId,
            rs.getBoolean("liked"),
            rs.getBoolean("favorited"),
            rs.getBoolean("followed"),
            coalesce(rs.getString("scene_tag"), "校园"),
            coalesce(rs.getString("style_tag"), "极简"),
            coalesce(rs.getString("budget_tag"), "100-150"),
            rs.getInt("like_count"),
            rs.getInt("comment_count"),
            rs.getInt("favorite_count"),
            rs.getInt("share_count"),
            formatPrice(rs.getBigDecimal("price_amount")),
            coalesce(rs.getString("product_name"), rs.getString("title")),
            coalesce(rs.getString("platform_name"), "外部平台"),
            coalesce(rs.getString("profit_label"), "导购说明"),
            coalesce(rs.getString("guide_tip"), "请结合自身需求与预算理性消费。"),
            findHighlights(rs.getString("post_code")),
            findCommentPreview(rs.getString("post_code"))
        ), viewerUserId, viewerUserId, viewerUserId, viewerUserId, postId);
        if (list.isEmpty()) {
            throw new BusinessException("未找到对应的穿搭内容");
        }
        return list.get(0);
    }

    @Override
    @Transactional
    public PostCreateResultVO create(PostCreateRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        Set<String> sceneOptions = loadTagValues("scene");
        Set<String> styleOptions = loadTagValues("style");
        Set<String> budgetOptions = loadTagValues("budget");

        String sceneTag = pickTag(request.tags(), sceneOptions, "校园");
        String styleTag = pickTag(request.tags(), styleOptions, "极简");
        String budgetTag = pickTag(request.tags(), budgetOptions, "100-150");
        String subtitle = buildSubtitle(request.desc());
        String coverTag = sceneTag + "精选";
        String coverImageUrl = request.imageUrls() == null || request.imageUrls().isEmpty() ? null : request.imageUrls().get(0);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into post (
                    user_id, title, subtitle, description, scene_tag, style_tag, budget_tag,
                    cover_tag, cover_image_url, status, audit_status, like_count, comment_count,
                    favorite_count, share_count, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 1, 0, 0, 0, 0, now(), now())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, currentUserId);
            statement.setString(2, request.title());
            statement.setString(3, subtitle);
            statement.setString(4, request.desc());
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

        if (request.imageUrls() != null) {
            for (int i = 0; i < request.imageUrls().size(); i += 1) {
                jdbcTemplate.update(
                    "insert into post_image (post_id, image_url, sort_order, created_at) values (?, ?, ?, now())",
                    postId,
                    request.imageUrls().get(i),
                    i + 1
                );
            }
        }

        for (String tag : new LinkedHashSet<>(request.tags())) {
            jdbcTemplate.update(
                "insert into post_tag (post_id, tag_type, tag_value, created_at) values (?, ?, ?, now())",
                postId,
                resolveTagType(tag, sceneOptions, styleOptions, budgetOptions),
                tag
            );
        }

        jdbcTemplate.update(
            """
            insert into product_link (
                post_id, product_name, platform_name, product_url, link_status,
                is_partner_product, commission_rate, price_amount, profit_label, guide_tip, last_checked_at, created_at
            ) values (?, ?, ?, ?, 1, 0, 5.00, ?, ?, ?, now(), now())
            """,
            postId,
            request.title(),
            detectPlatform(request.productLink()),
            request.productLink(),
            estimatePrice(budgetTag),
            "审核通过后开启佣金统计",
            "请根据自身实际需求理性下单。"
        );

        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            currentUserId,
            "系统通知",
            "你的穿搭已发布",
            "你的穿搭《" + request.title() + "》已加入内容管理列表。"
        );

        return new PostCreateResultVO(postCode, "CREATED", "发布成功");
    }

    @Override
    public List<PostCommentVO> listComments(String postId) {
        PostMeta meta = resolvePostMeta(postId);
        String sql = """
            select
                c.id,
                c.content,
                c.like_count,
                c.created_at,
                u.nickname,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class
            from post_comment c
            join app_user u on u.id = c.user_id
            left join user_profile up on up.user_id = u.id
            where c.post_id = ? and c.status = 1
            order by c.created_at desc, c.id desc
            """;
        return jdbcTemplate.query(sql, this::mapComment, meta.id());
    }

    @Override
    @Transactional
    public PostCommentVO createComment(String postId, PostCommentCreateRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        PostMeta meta = resolvePostMeta(postId);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into post_comment (post_id, user_id, content, like_count, status, created_at) values (?, ?, ?, 0, 1, now())",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, meta.id());
            statement.setLong(2, currentUserId);
            statement.setString(3, request.content());
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
        return buildCurrentUserComment(commentId, request.content(), currentUserId);
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
            return new PostInteractionVO(false, fetchCount(meta.id(), "like_count"));
        }
        jdbcTemplate.update("insert into post_like (post_id, user_id, created_at) values (?, ?, now())", meta.id(), currentUserId);
        jdbcTemplate.update("update post set like_count = like_count + 1 where id = ?", meta.id());
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
        return new PostCardVO(
            rs.getString("post_code"),
            coalesce(rs.getString("cover_tag"), "校园精选"),
            rs.getString("title"),
            coalesce(rs.getString("subtitle"), buildSubtitle(rs.getString("description"))),
            coalesce(rs.getString("description"), "暂时还没有内容介绍。"),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_class"), ""),
            joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
            coalesce(rs.getString("scene_tag"), "校园"),
            coalesce(rs.getString("style_tag"), "极简"),
            coalesce(rs.getString("budget_tag"), "100-150"),
            rs.getInt("like_count"),
            rs.getInt("comment_count"),
            rs.getInt("favorite_count"),
            rs.getInt("share_count"),
            formatPrice(rs.getBigDecimal("price_amount")),
            coalesce(rs.getString("product_name"), rs.getString("title")),
            coalesce(rs.getString("platform_name"), "外部平台"),
            coalesce(rs.getString("profit_label"), "导购说明"),
            coalesce(rs.getString("guide_tip"), "请结合自身需求与预算理性消费。")
        );
    }

    private PostCommentVO mapComment(ResultSet rs, int rowNum) throws SQLException {
        return new PostCommentVO(
            String.valueOf(rs.getLong("id")),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_class"), ""),
            rs.getString("content"),
            formatRelativeTime(rs.getTimestamp("created_at")),
            rs.getInt("like_count")
        );
    }

    private UserCardVO mapUserCard(ResultSet rs, int rowNum) throws SQLException {
        return new UserCardVO(
            rs.getLong("id"),
            rs.getString("nickname"),
            coalesce(rs.getString("avatar_text"), "C"),
            coalesce(rs.getString("avatar_class"), ""),
            buildIntro(rs.getString("school_name"), rs.getString("grade_name"), rs.getString("signature")),
            rs.getBoolean("active")
        );
    }

    private PostCommentVO buildCurrentUserComment(Long id, String content, long currentUserId) {
        return jdbcTemplate.queryForObject(
            """
            select u.nickname, coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                   coalesce(up.avatar_class, '') as avatar_class
            from app_user u
            left join user_profile up on up.user_id = u.id
            where u.id = ?
            """,
            (rs, rowNum) -> new PostCommentVO(
                String.valueOf(id == null ? 0 : id),
                rs.getString("nickname"),
                coalesce(rs.getString("avatar_text"), "C"),
                coalesce(rs.getString("avatar_class"), "soft"),
                content,
                "刚刚",
                0
            ),
            currentUserId
        );
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

    private PostMeta resolvePostMeta(String postCode) {
        List<PostMeta> list = jdbcTemplate.query(
            "select id, user_id, title from post where post_code = ? and status = 1 and audit_status in (0, 1, 2)",
            (rs, rowNum) -> new PostMeta(rs.getLong("id"), rs.getLong("user_id"), rs.getString("title")),
            postCode
        );
        if (list.isEmpty()) {
            throw new BusinessException("未找到对应的穿搭内容");
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

    private BigDecimal estimatePrice(String budgetTag) {
        if (budgetTag == null || budgetTag.isBlank()) {
            return new BigDecimal("129");
        }
        if (budgetTag.endsWith("+")) {
            String number = budgetTag.substring(0, budgetTag.length() - 1);
            try {
                return new BigDecimal(number).add(new BigDecimal("29"));
            } catch (NumberFormatException exception) {
                return new BigDecimal("229");
            }
        }
        if (budgetTag.contains("-")) {
            String[] parts = budgetTag.split("-");
            if (parts.length == 2) {
                try {
                    BigDecimal low = new BigDecimal(parts[0].trim());
                    BigDecimal high = new BigDecimal(parts[1].trim());
                    return low.add(high).divide(new BigDecimal("2"), 0, RoundingMode.HALF_UP);
                } catch (NumberFormatException ignored) {
                    return new BigDecimal("129");
                }
            }
        }
        return new BigDecimal("129");
    }

    private String formatPrice(BigDecimal amount) {
        if (amount == null) {
            return "￥0";
        }
        return "￥" + amount.stripTrailingZeros().toPlainString();
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

    private record PostMeta(long id, long authorUserId, String title) {
    }
}