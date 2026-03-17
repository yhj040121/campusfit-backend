package com.campusfit.modules.profile.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.auth.service.UserAuthService;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.profile.dto.ProfileUpdateRequest;
import com.campusfit.modules.profile.service.ProfileService;
import com.campusfit.modules.profile.vo.FollowToggleVO;
import com.campusfit.modules.profile.vo.ProfileEditVO;
import com.campusfit.modules.profile.vo.ProfileSummaryVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final JdbcTemplate jdbcTemplate;
    private final UserAuthService userAuthService;

    public ProfileServiceImpl(JdbcTemplate jdbcTemplate, UserAuthService userAuthService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userAuthService = userAuthService;
    }

    @Override
    public ProfileSummaryVO getCurrentProfile() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = """
            select
                u.nickname,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                up.school_name,
                up.grade_name,
                up.signature,
                coalesce((select count(*) from user_follow uf where uf.follower_user_id = u.id), 0) as following_count,
                coalesce((select count(*) from user_follow uf where uf.followee_user_id = u.id), 0) as follower_count,
                coalesce((select sum(p.like_count) from post p where p.user_id = u.id and p.status = 1 and p.audit_status = 1), 0) as like_count,
                coalesce((select sum(cr.commission_amount) from commission_record cr where cr.user_id = u.id), 0) as income_total,
                coalesce((select count(*) from creator_cooperation cc where cc.user_id = u.id and cc.cooperation_status in (0, 1)), 0) as cooperation_count
            from app_user u
            left join user_profile up on up.user_id = u.id
            where u.id = ?
            """;
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ProfileSummaryVO(
                rs.getString("nickname"),
                rs.getString("avatar_text"),
                joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
                coalesce(rs.getString("signature"), "简单介绍一下你的穿搭风格吧。"),
                rs.getInt("following_count"),
                rs.getInt("follower_count"),
                rs.getInt("like_count"),
                formatCurrency(rs.getBigDecimal("income_total")),
                rs.getInt("cooperation_count")
            ), currentUserId);
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException("未找到当前用户资料");
        }
    }

    @Override
    public ProfileEditVO getCurrentProfileForEdit() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = """
            select u.phone, u.nickname, up.school_name, up.grade_name, up.signature
            from app_user u
            left join user_profile up on up.user_id = u.id
            where u.id = ?
            """;
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ProfileEditVO(
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("school_name"),
                rs.getString("grade_name"),
                rs.getString("signature")
            ), currentUserId);
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException("未找到当前用户资料");
        }
    }

    @Override
    @Transactional
    public ProfileSummaryVO updateCurrentProfile(ProfileUpdateRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        String nickname = request.nickname().trim();
        String schoolName = normalize(request.schoolName());
        String gradeName = normalize(request.gradeName());
        String signature = normalize(request.signature());
        String avatarText = nickname.substring(0, 1);

        int updatedUser = jdbcTemplate.update(
            "update app_user set nickname = ?, updated_at = now() where id = ?",
            nickname,
            currentUserId
        );
        if (updatedUser == 0) {
            throw new BusinessException("未找到当前用户账号");
        }

        Integer profileCount = jdbcTemplate.queryForObject(
            "select count(*) from user_profile where user_id = ?",
            Integer.class,
            currentUserId
        );
        if (profileCount != null && profileCount > 0) {
            jdbcTemplate.update(
                "update user_profile set school_name = ?, grade_name = ?, signature = ?, avatar_text = ?, updated_at = now() where user_id = ?",
                schoolName,
                gradeName,
                signature,
                avatarText,
                currentUserId
            );
        } else {
            jdbcTemplate.update(
                "insert into user_profile (user_id, school_name, grade_name, signature, avatar_text, avatar_class, like_count, follower_count, following_count, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 0, 0, 0, now(), now())",
                currentUserId,
                schoolName,
                gradeName,
                signature,
                avatarText,
                "soft"
            );
        }

        userAuthService.refreshNickname(currentUserId, nickname);
        return getCurrentProfile();
    }

    @Override
    public List<UserCardVO> listFollows(String type) {
        long currentUserId = UserAuthContext.requireUserId();
        if ("fans".equalsIgnoreCase(type)) {
            String fansSql = """
                select
                    u.id,
                    u.nickname,
                    coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                    coalesce(up.avatar_class, '') as avatar_class,
                    up.school_name,
                    up.grade_name,
                    up.signature,
                    exists(
                        select 1
                        from user_follow uf2
                        where uf2.follower_user_id = ?
                          and uf2.followee_user_id = u.id
                    ) as active
                from user_follow uf
                join app_user u on u.id = uf.follower_user_id
                left join user_profile up on up.user_id = u.id
                where uf.followee_user_id = ?
                order by uf.created_at desc, uf.id desc
                """;
            return jdbcTemplate.query(fansSql, this::mapUserCard, currentUserId, currentUserId);
        }
        String followingSql = """
            select
                u.id,
                u.nickname,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                coalesce(up.avatar_class, '') as avatar_class,
                up.school_name,
                up.grade_name,
                up.signature,
                true as active
            from user_follow uf
            join app_user u on u.id = uf.followee_user_id
            left join user_profile up on up.user_id = u.id
            where uf.follower_user_id = ?
            order by uf.created_at desc, uf.id desc
            """;
        return jdbcTemplate.query(followingSql, this::mapUserCard, currentUserId);
    }

    @Override
    @Transactional
    public FollowToggleVO toggleFollow(Long targetUserId) {
        long currentUserId = UserAuthContext.requireUserId();
        if (targetUserId == null) {
            throw new BusinessException("目标用户不能为空");
        }
        if (currentUserId == targetUserId) {
            throw new BusinessException("不能关注自己");
        }
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from user_follow where follower_user_id = ? and followee_user_id = ?",
            Integer.class,
            currentUserId,
            targetUserId
        );
        boolean active = exists != null && exists > 0;
        if (active) {
            jdbcTemplate.update(
                "delete from user_follow where follower_user_id = ? and followee_user_id = ?",
                currentUserId,
                targetUserId
            );
            return new FollowToggleVO(false);
        }
        jdbcTemplate.update(
            "insert into user_follow (follower_user_id, followee_user_id, created_at) values (?, ?, now())",
            currentUserId,
            targetUserId
        );
        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            targetUserId,
            "互动通知",
            "你有一位新粉丝",
            "有校园用户关注了你的主页。"
        );
        return new FollowToggleVO(true);
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

    private String formatCurrency(BigDecimal amount) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        return "￥" + safeAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}