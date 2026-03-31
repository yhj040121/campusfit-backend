package com.campusfit.modules.profile.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.common.vo.UserCardVO;
import com.campusfit.modules.auth.service.UserAuthService;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.profile.dto.IncentiveWithdrawRequest;
import com.campusfit.modules.profile.dto.ProfileUpdateRequest;
import com.campusfit.modules.profile.service.ProfileService;
import com.campusfit.modules.profile.vo.FollowToggleVO;
import com.campusfit.modules.profile.vo.ProfileEditVO;
import com.campusfit.modules.profile.vo.ProfileIncentiveCenterVO;
import com.campusfit.modules.profile.vo.ProfileIncentiveRecordVO;
import com.campusfit.modules.profile.vo.ProfileSummaryVO;
import com.campusfit.modules.profile.vo.ProfileWithdrawRequestVO;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class ProfileServiceImpl implements ProfileService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final BigDecimal MIN_WITHDRAW_AMOUNT = new BigDecimal("10.00");
    private static final BigDecimal WITHDRAW_FEE_RATE = new BigDecimal("0.02");

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
                u.avatar_url,
                coalesce(up.avatar_text, substring(u.nickname, 1, 1)) as avatar_text,
                up.cover_image_url,
                up.school_name,
                up.grade_name,
                up.gender,
                up.email,
                up.location_name,
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
                rs.getString("avatar_url"),
                rs.getString("cover_image_url"),
                joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
                coalesce(normalizeGenderValue(rs.getString("gender")), ""),
                coalesce(rs.getString("email"), ""),
                coalesce(rs.getString("location_name"), ""),
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
            select u.phone, u.nickname, u.avatar_url, up.cover_image_url, up.gender, up.email, up.location_name, up.school_name, up.grade_name, up.signature
            from app_user u
            left join user_profile up on up.user_id = u.id
            where u.id = ?
            """;
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new ProfileEditVO(
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getString("cover_image_url"),
                normalizeGenderValue(rs.getString("gender")),
                rs.getString("email"),
                rs.getString("location_name"),
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
        String avatarUrl = normalize(request.avatarUrl());
        String coverImageUrl = normalize(request.coverImageUrl());
        String gender = normalizeGenderValue(request.gender());
        String email = normalize(request.email());
        String locationName = normalize(request.locationName());
        String schoolName = normalize(request.schoolName());
        String gradeName = normalize(request.gradeName());
        String signature = normalize(request.signature());
        String avatarText = nickname.substring(0, 1);

        int updatedUser = jdbcTemplate.update(
            "update app_user set nickname = ?, avatar_url = ?, updated_at = now() where id = ?",
            nickname,
            avatarUrl,
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
                "update user_profile set school_name = ?, grade_name = ?, signature = ?, avatar_text = ?, cover_image_url = ?, gender = ?, email = ?, location_name = ?, updated_at = now() where user_id = ?",
                schoolName,
                gradeName,
                signature,
                avatarText,
                coverImageUrl,
                gender,
                email,
                locationName,
                currentUserId
            );
        } else {
            jdbcTemplate.update(
                "insert into user_profile (user_id, school_name, grade_name, signature, avatar_text, avatar_class, cover_image_url, gender, email, location_name, like_count, follower_count, following_count, created_at, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, 0, now(), now())",
                currentUserId,
                schoolName,
                gradeName,
                signature,
                avatarText,
                "soft",
                coverImageUrl,
                gender,
                email,
                locationName
            );
        }

        userAuthService.refreshNickname(currentUserId, nickname);
        return getCurrentProfile();
    }

    @Override
    public ProfileIncentiveCenterVO getCurrentIncentiveCenter() {
        long currentUserId = UserAuthContext.requireUserId();
        IncentiveTotals incentiveTotals = jdbcTemplate.queryForObject("""
            select
                coalesce(sum(cr.commission_amount), 0) as total_amount,
                coalesce(sum(case when cr.settlement_status = 1 then cr.commission_amount else 0 end), 0) as settled_amount,
                coalesce(sum(case when cr.settlement_status = 0 then cr.commission_amount else 0 end), 0) as pending_settlement_amount,
                coalesce(sum(case when cr.settlement_status = 1 then 1 else 0 end), 0) as settled_count,
                coalesce(sum(case when cr.settlement_status = 0 then 1 else 0 end), 0) as pending_count
            from commission_record cr
            where cr.user_id = ?
            """, (rs, rowNum) -> new IncentiveTotals(
            safeAmount(rs.getBigDecimal("total_amount")),
            safeAmount(rs.getBigDecimal("settled_amount")),
            safeAmount(rs.getBigDecimal("pending_settlement_amount")),
            rs.getInt("settled_count"),
            rs.getInt("pending_count")
        ), currentUserId);
        WithdrawTotals withdrawTotals = queryWithdrawTotals(currentUserId);
        BigDecimal availableAmount = incentiveTotals.settledAmount()
            .subtract(withdrawTotals.pendingAmount())
            .subtract(withdrawTotals.withdrawnAmount())
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
        boolean canWithdraw = availableAmount.compareTo(MIN_WITHDRAW_AMOUNT) >= 0;
        String withdrawHint = buildWithdrawHint(availableAmount);
        List<ProfileIncentiveRecordVO> settlementRecords = jdbcTemplate.query("""
            select
                cr.id,
                cr.post_id,
                coalesce(p.title, '校园内容') as post_title,
                cr.income_type,
                cr.commission_amount,
                cr.settlement_status,
                cr.created_at
            from commission_record cr
            left join post p on p.id = cr.post_id
            where cr.user_id = ?
            order by cr.created_at desc, cr.id desc
            limit 20
            """, (rs, rowNum) -> new ProfileIncentiveRecordVO(
            rs.getLong("id"),
            rs.getLong("post_id"),
            rs.getString("post_title"),
            normalizeSettlementType(rs.getString("income_type")),
            formatCurrency(rs.getBigDecimal("commission_amount")),
            rs.getInt("settlement_status") == 1 ? "已结算" : "待结算",
            rs.getInt("settlement_status"),
            formatDateTime(rs.getTimestamp("created_at"))
        ), currentUserId);
        List<ProfileWithdrawRequestVO> withdrawRequests = jdbcTemplate.query("""
            select
                cwr.id,
                cwr.request_amount,
                cwr.request_status,
                cwr.created_at,
                cwr.processed_at,
                cwr.remark
            from creator_withdraw_request cwr
            where cwr.user_id = ?
            order by cwr.created_at desc, cwr.id desc
            limit 10
            """, (rs, rowNum) -> new ProfileWithdrawRequestVO(
            rs.getLong("id"),
            formatCurrency(rs.getBigDecimal("request_amount")),
            formatCurrency(calculateWithdrawFee(rs.getBigDecimal("request_amount"))),
            formatCurrency(calculateWithdrawNetAmount(rs.getBigDecimal("request_amount"))),
            mapWithdrawStatus(rs.getInt("request_status")),
            rs.getInt("request_status"),
            formatDateTime(rs.getTimestamp("created_at")),
            formatDateTime(rs.getTimestamp("processed_at")),
            coalesce(rs.getString("remark"), "平台处理中")
        ), currentUserId);
        return new ProfileIncentiveCenterVO(
            formatCurrency(incentiveTotals.totalAmount()),
            formatCurrency(availableAmount),
            availableAmount.toPlainString(),
            formatCurrency(incentiveTotals.pendingSettlementAmount()),
            formatCurrency(withdrawTotals.pendingAmount()),
            formatCurrency(withdrawTotals.withdrawnAmount()),
            incentiveTotals.settledCount(),
            incentiveTotals.pendingCount(),
            canWithdraw,
            withdrawHint,
            formatCurrency(MIN_WITHDRAW_AMOUNT),
            WITHDRAW_FEE_RATE.toPlainString(),
            settlementRecords,
            withdrawRequests
        );
    }

    @Override
    @Transactional
    public ProfileWithdrawRequestVO applyWithdraw(IncentiveWithdrawRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        WithdrawTotals withdrawTotals = queryWithdrawTotals(currentUserId);
        BigDecimal settledAmount = jdbcTemplate.queryForObject("""
            select coalesce(sum(cr.commission_amount), 0)
            from commission_record cr
            where cr.user_id = ? and cr.settlement_status = 1
            """, BigDecimal.class, currentUserId);
        BigDecimal availableAmount = safeAmount(settledAmount)
            .subtract(withdrawTotals.pendingAmount())
            .subtract(withdrawTotals.withdrawnAmount())
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
        if (availableAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("当前暂无可提现余额");
        }
        BigDecimal requestAmount = request == null || request.amount() == null
            ? availableAmount
            : request.amount().setScale(2, RoundingMode.HALF_UP);
        if (requestAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("提现金额必须大于 0");
        }
        if (requestAmount.compareTo(MIN_WITHDRAW_AMOUNT) < 0) {
            throw new BusinessException("提现金额不能低于 10 元");
        }
        if (requestAmount.compareTo(availableAmount) > 0) {
            throw new BusinessException("提现金额不能超过可提现余额");
        }
        jdbcTemplate.update("""
            insert into creator_withdraw_request (user_id, request_amount, request_status, created_at, processed_at, remark)
            values (?, ?, 0, now(), null, ?)
            """, currentUserId, requestAmount, "已提交申请，等待平台审核");
        jdbcTemplate.update("""
            insert into message_notification (user_id, message_type, title, content, read_status, created_at)
            values (?, ?, ?, ?, 0, now())
            """, currentUserId, "激励通知", "提现申请已提交",
            "你已提交 " + formatCurrency(requestAmount) + " 的创作激励提现申请，平台审核后会同步进度。");
        return jdbcTemplate.queryForObject("""
            select
                cwr.id,
                cwr.request_amount,
                cwr.request_status,
                cwr.created_at,
                cwr.processed_at,
                cwr.remark
            from creator_withdraw_request cwr
            where cwr.user_id = ?
            order by cwr.id desc
            limit 1
            """, (rs, rowNum) -> new ProfileWithdrawRequestVO(
            rs.getLong("id"),
            formatCurrency(rs.getBigDecimal("request_amount")),
            formatCurrency(calculateWithdrawFee(rs.getBigDecimal("request_amount"))),
            formatCurrency(calculateWithdrawNetAmount(rs.getBigDecimal("request_amount"))),
            mapWithdrawStatus(rs.getInt("request_status")),
            rs.getInt("request_status"),
            formatDateTime(rs.getTimestamp("created_at")),
            formatDateTime(rs.getTimestamp("processed_at")),
            coalesce(rs.getString("remark"), "平台处理中")
        ), currentUserId);
    }

    @Override
    public List<UserCardVO> listFollows(String type) {
        long currentUserId = UserAuthContext.requireUserId();
        if ("fans".equalsIgnoreCase(type)) {
            String fansSql = """
                select
                    u.id,
                    u.nickname,
                    u.avatar_url,
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
                u.avatar_url,
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
            coalesce(rs.getString("avatar_url"), ""),
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
        BigDecimal safeAmount = safeAmount(amount);
        return "¥" + safeAmount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private BigDecimal safeAmount(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private BigDecimal calculateWithdrawFee(BigDecimal requestAmount) {
        return safeAmount(requestAmount)
            .multiply(WITHDRAW_FEE_RATE)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateWithdrawNetAmount(BigDecimal requestAmount) {
        return safeAmount(requestAmount)
            .subtract(calculateWithdrawFee(requestAmount))
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private String buildWithdrawHint(BigDecimal availableAmount) {
        BigDecimal safeAvailableAmount = safeAmount(availableAmount).setScale(2, RoundingMode.HALF_UP);
        if (safeAvailableAmount.compareTo(MIN_WITHDRAW_AMOUNT) >= 0) {
            return "已结算收益支持自定义提现吗：最低 10 元，平台收取 2% 手续费。";
        }
        if (safeAvailableAmount.compareTo(BigDecimal.ZERO) > 0) {
            return "当前可提现金额未达到 10 元门槛，累计满 10 元后可申请提现，平台收取 2% 手续费。";
        }
        return "当前暂无可提现余额，待结算记录会在月结算完成后自动转入可提现余额。";
    }

    private String normalizeSettlementType(String type) {
        if (type == null || type.isBlank()) {
            return "创作激励";
        }
        return switch (type) {
            case "导购佣金", "导购收益", "推广佣金" -> "推广激励";
            case "品牌分成", "合作分成" -> "品牌合作奖励";
            default -> type;
        };
    }

    private String mapWithdrawStatus(int status) {
        return switch (status) {
            case 1 -> "已打款";
            case 2 -> "已驳回";
            default -> "审核中";
        };
    }

    private WithdrawTotals queryWithdrawTotals(long currentUserId) {
        return jdbcTemplate.queryForObject("""
            select
                coalesce(sum(case when cwr.request_status = 0 then cwr.request_amount else 0 end), 0) as pending_amount,
                coalesce(sum(case when cwr.request_status = 1 then cwr.request_amount else 0 end), 0) as withdrawn_amount
            from creator_withdraw_request cwr
            where cwr.user_id = ?
            """, (rs, rowNum) -> new WithdrawTotals(
            safeAmount(rs.getBigDecimal("pending_amount")),
            safeAmount(rs.getBigDecimal("withdrawn_amount"))
        ), currentUserId);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeGenderValue(String value) {
        String normalized = normalize(value);
        if ("male".equalsIgnoreCase(normalized)) {
            return "male";
        }
        if ("female".equalsIgnoreCase(normalized)) {
            return "female";
        }
        return null;
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record IncentiveTotals(
        BigDecimal totalAmount,
        BigDecimal settledAmount,
        BigDecimal pendingSettlementAmount,
        int settledCount,
        int pendingCount
    ) {
    }

    private record WithdrawTotals(
        BigDecimal pendingAmount,
        BigDecimal withdrawnAmount
    ) {
    }
}
