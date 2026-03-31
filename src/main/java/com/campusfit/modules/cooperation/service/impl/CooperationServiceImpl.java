package com.campusfit.modules.cooperation.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.cooperation.service.CooperationService;
import com.campusfit.modules.cooperation.vo.CooperationItemVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class CooperationServiceImpl implements CooperationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int STATUS_PENDING = 0;
    private static final int STATUS_RUNNING = 1;
    private static final int STATUS_REWARD_READY = 2;
    private static final int STATUS_REWARD_ISSUED = 3;
    private static final int STATUS_ABANDONED = 4;

    private static final String COOPERATION_SELECT = """
        select
            c.id,
            c.cooperation_code,
            c.cooperation_title,
            c.cooperation_desc,
            c.cooperation_status,
            c.reward_amount,
            c.target_post_count,
            coalesce(c.target_like_count, 0) as target_like_count,
            c.deadline_at,
            c.accepted_at,
            c.reward_issued_at,
            c.abandoned_at,
            m.merchant_name,
            coalesce(post_stats.submitted_post_count, 0) as submitted_post_count,
            coalesce(post_stats.approved_post_count, 0) as approved_post_count,
            coalesce(post_stats.approved_like_count, 0) as approved_like_count
        from creator_cooperation c
        join merchant m on m.id = c.merchant_id
        left join (
            select
                p.cooperation_id,
                sum(case when p.status = 1 and p.audit_status in (0, 1) then 1 else 0 end) as submitted_post_count,
                sum(case when p.status = 1 and p.audit_status = 1 then 1 else 0 end) as approved_post_count,
                coalesce(sum(case when p.status = 1 and p.audit_status = 1 then greatest(coalesce(p.like_count, 0), 0) else 0 end), 0) as approved_like_count
            from post p
            where p.cooperation_id is not null
            group by p.cooperation_id
        ) post_stats on post_stats.cooperation_id = c.id
        """;

    private final JdbcTemplate jdbcTemplate;

    public CooperationServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<CooperationItemVO> listMine() {
        long currentUserId = UserAuthContext.requireUserId();
        return jdbcTemplate.query(
            COOPERATION_SELECT + """
                 where c.user_id = ?
                 order by
                     case when c.cooperation_status in (0, 1, 2) then 0 else 1 end,
                     c.id desc
                """,
            (rs, rowNum) -> mapCooperation(rs),
            currentUserId
        );
    }

    @Override
    @Transactional
    public CooperationItemVO accept(String cooperationCode) {
        long currentUserId = UserAuthContext.requireUserId();
        CooperationSnapshot snapshot = requireOwnedCooperation(cooperationCode, currentUserId);
        if (snapshot.statusCode() == STATUS_REWARD_ISSUED) {
            throw new BusinessException("该合作单奖励已发放");
        }
        if (snapshot.statusCode() == STATUS_ABANDONED) {
            throw new BusinessException("该合作单已放弃，不能重新接受");
        }
        if (snapshot.acceptedAt() == null) {
            jdbcTemplate.update(
                """
                update creator_cooperation
                set cooperation_status = 1,
                    accepted_at = now(),
                    updated_at = now()
                where id = ?
                """,
                snapshot.id()
            );
            jdbcTemplate.update(
                """
                insert into message_notification (user_id, message_type, title, content, read_status, created_at)
                values (?, ?, ?, ?, 0, now())
                """,
                currentUserId,
                "合作通知",
                "你已接受合作邀请",
                "合作单《" + snapshot.title() + "》已进入执行中，发布并绑定内容后即可开始累计达标进度。"
            );
        }
        syncProgressByCooperationId(snapshot.id());
        return findByCode(cooperationCode);
    }

    @Override
    @Transactional
    public CooperationItemVO abandon(String cooperationCode) {
        long currentUserId = UserAuthContext.requireUserId();
        CooperationSnapshot snapshot = requireOwnedCooperation(cooperationCode, currentUserId);
        if (snapshot.statusCode() == STATUS_REWARD_ISSUED) {
            throw new BusinessException("该合作单奖励已发放，不能再放弃");
        }
        if (snapshot.statusCode() == STATUS_REWARD_READY) {
            throw new BusinessException("该合作单已达标，不能再放弃");
        }
        if (snapshot.statusCode() == STATUS_ABANDONED) {
            return findByCode(cooperationCode);
        }
        boolean rejectingInvite = snapshot.acceptedAt() == null || snapshot.statusCode() == STATUS_PENDING;

        jdbcTemplate.update(
            """
            update creator_cooperation
            set cooperation_status = ?,
                abandoned_at = now(),
                updated_at = now()
            where id = ?
            """,
            STATUS_ABANDONED,
            snapshot.id()
        );
        jdbcTemplate.update(
            """
            insert into message_notification (user_id, message_type, title, content, read_status, created_at)
            values (?, ?, ?, ?, 0, now())
            """,
            currentUserId,
            "合作通知",
            rejectingInvite ? "你已拒绝合作邀请" : "你已放弃合作单",
            rejectingInvite
                ? "合作邀请《" + snapshot.title() + "》已拒绝，后续将不再进入执行流程。"
                : "合作单《" + snapshot.title() + "》已被放弃，后续将不再计入奖励进度。"
        );
        return findByCode(cooperationCode);
    }

    @Override
    public CooperationItemVO findByCode(String cooperationCode) {
        String normalizedCode = normalizeOptionalText(cooperationCode);
        if (normalizedCode == null) {
            return null;
        }
        Long currentUserId = UserAuthContext.getCurrentUserId();
        if (currentUserId == null) {
            return null;
        }
        List<CooperationItemVO> list = jdbcTemplate.query(
            COOPERATION_SELECT + " where c.user_id = ? and c.cooperation_code = ? limit 1",
            (rs, rowNum) -> mapCooperation(rs),
            currentUserId,
            normalizedCode
        );
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public CooperationItemVO findByPostCode(String postCode) {
        String normalizedPostCode = normalizeOptionalText(postCode);
        if (normalizedPostCode == null) {
            return null;
        }
        List<CooperationItemVO> list = jdbcTemplate.query(
            COOPERATION_SELECT + """
                 where exists (
                     select 1
                     from post p
                     where p.cooperation_id = c.id and p.post_code = ?
                 )
                 limit 1
                """,
            (rs, rowNum) -> mapCooperation(rs),
            normalizedPostCode
        );
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    @Transactional
    public void bindPostToCooperation(long postId, long userId, String cooperationCode) {
        Long previousCooperationId = queryCooperationIdByPostId(postId);
        String normalizedCode = normalizeOptionalText(cooperationCode);

        if (previousCooperationId != null && normalizedCode == null) {
            throw new BusinessException("该内容已绑定合作单，如需退出请先放弃对应合作");
        }

        Long nextCooperationId = null;
        if (normalizedCode != null) {
            CooperationSnapshot snapshot = requireOwnedCooperation(normalizedCode, userId);
            validateCanBind(snapshot);
            nextCooperationId = snapshot.id();
        }

        if (previousCooperationId != null && nextCooperationId != null && !previousCooperationId.equals(nextCooperationId)) {
            throw new BusinessException("该内容已绑定其他合作单，不能改绑");
        }
        if (previousCooperationId != null && previousCooperationId.equals(nextCooperationId)) {
            syncProgressByCooperationId(previousCooperationId);
            return;
        }
        if (previousCooperationId == null && nextCooperationId == null) {
            return;
        }

        jdbcTemplate.update(
            "update post set cooperation_id = ?, updated_at = now() where id = ?",
            nextCooperationId,
            postId
        );

        if (previousCooperationId != null && !previousCooperationId.equals(nextCooperationId)) {
            syncProgressByCooperationId(previousCooperationId);
        }
        if (nextCooperationId != null) {
            syncProgressByCooperationId(nextCooperationId);
        }
    }

    @Override
    public void syncProgressByPostId(long postId) {
        syncProgressByCooperationId(queryCooperationIdByPostId(postId));
    }

    @Override
    @Transactional
    public void syncProgressByCooperationId(Long cooperationId) {
        if (cooperationId == null) {
            return;
        }
        CooperationStatusSnapshot snapshot = jdbcTemplate.query(
            """
            select id, cooperation_status, accepted_at, reward_issued_at,
                   greatest(target_post_count, 1) as target_post_count,
                   greatest(coalesce(target_like_count, 0), 0) as target_like_count
            from creator_cooperation
            where id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new CooperationStatusSnapshot(
                    rs.getLong("id"),
                    rs.getInt("cooperation_status"),
                    rs.getTimestamp("accepted_at"),
                    rs.getTimestamp("reward_issued_at"),
                    rs.getInt("target_post_count"),
                    rs.getInt("target_like_count")
                )
                : null,
            cooperationId
        );
        if (snapshot == null || snapshot.rewardIssuedAt() != null
            || snapshot.statusCode() == STATUS_REWARD_ISSUED
            || snapshot.statusCode() == STATUS_ABANDONED) {
            return;
        }

        if (snapshot.acceptedAt() == null) {
            if (snapshot.statusCode() != STATUS_PENDING) {
                jdbcTemplate.update(
                    "update creator_cooperation set cooperation_status = 0, updated_at = now() where id = ?",
                    cooperationId
                );
            }
            return;
        }

        CooperationProgress progress = queryProgress(cooperationId);
        int nextStatus = progress.approvedPostCount() >= snapshot.targetPostCount()
            && progress.approvedLikeCount() >= snapshot.targetLikeCount()
            ? STATUS_REWARD_READY
            : STATUS_RUNNING;
        if (nextStatus != snapshot.statusCode()) {
            jdbcTemplate.update(
                "update creator_cooperation set cooperation_status = ?, updated_at = now() where id = ?",
                nextStatus,
                cooperationId
            );
            if (nextStatus == STATUS_REWARD_READY) {
                notifyRewardReady(cooperationId);
            }
        }
    }

    private void notifyRewardReady(Long cooperationId) {
        RewardReadySnapshot snapshot = jdbcTemplate.query(
            """
            select id, user_id, cooperation_title
            from creator_cooperation
            where id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new RewardReadySnapshot(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("cooperation_title")
                )
                : null,
            cooperationId
        );
        if (snapshot == null) {
            return;
        }
        Integer exists = jdbcTemplate.queryForObject(
            """
            select count(*)
            from message_notification
            where user_id = ?
              and message_type = '合作通知'
              and title = '合作内容已达标'
              and content = ?
            """,
            Integer.class,
            snapshot.userId(),
            "合作单《" + snapshot.title() + "》的绑定内容已达标，正在等待管理员确认奖励。"
        );
        if (exists != null && exists > 0) {
            return;
        }
        jdbcTemplate.update(
            """
            insert into message_notification (user_id, message_type, title, content, read_status, created_at)
            values (?, ?, ?, ?, 0, now())
            """,
            snapshot.userId(),
            "合作通知",
            "合作内容已达标",
            "合作单《" + snapshot.title() + "》的绑定内容已达标，正在等待管理员确认奖励。"
        );
    }

    private CooperationProgress queryProgress(Long cooperationId) {
        return jdbcTemplate.query(
            """
            select
                coalesce(sum(case when p.status = 1 and p.audit_status in (0, 1) then 1 else 0 end), 0) as submitted_post_count,
                coalesce(sum(case when p.status = 1 and p.audit_status = 1 then 1 else 0 end), 0) as approved_post_count,
                coalesce(sum(case when p.status = 1 and p.audit_status = 1 then greatest(coalesce(p.like_count, 0), 0) else 0 end), 0) as approved_like_count
            from post p
            where p.cooperation_id = ?
            """,
            rs -> rs.next()
                ? new CooperationProgress(
                    rs.getInt("submitted_post_count"),
                    rs.getInt("approved_post_count"),
                    rs.getInt("approved_like_count")
                )
                : new CooperationProgress(0, 0, 0),
            cooperationId
        );
    }

    private void validateCanBind(CooperationSnapshot snapshot) {
        if (snapshot.acceptedAt() == null || snapshot.statusCode() == STATUS_PENDING) {
            throw new BusinessException("请先接受合作单，再发布绑定内容");
        }
        if (snapshot.statusCode() == STATUS_REWARD_READY) {
            throw new BusinessException("该合作单已达标，不能继续绑定新内容");
        }
        if (snapshot.statusCode() == STATUS_REWARD_ISSUED) {
            throw new BusinessException("该合作单奖励已发放，不能继续绑定新内容");
        }
        if (snapshot.statusCode() == STATUS_ABANDONED) {
            throw new BusinessException("该合作单已放弃，不能继续绑定新内容");
        }
    }

    private CooperationSnapshot requireOwnedCooperation(String cooperationCode, long userId) {
        CooperationSnapshot snapshot = jdbcTemplate.query(
            """
            select id, cooperation_title, cooperation_status, accepted_at, reward_issued_at,
                   greatest(target_post_count, 1) as target_post_count,
                   greatest(coalesce(target_like_count, 0), 0) as target_like_count,
                   abandoned_at
            from creator_cooperation
            where cooperation_code = ? and user_id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new CooperationSnapshot(
                    rs.getLong("id"),
                    rs.getString("cooperation_title"),
                    rs.getInt("cooperation_status"),
                    rs.getTimestamp("accepted_at"),
                    rs.getTimestamp("reward_issued_at"),
                    rs.getInt("target_post_count"),
                    rs.getInt("target_like_count"),
                    rs.getTimestamp("abandoned_at")
                )
                : null,
            cooperationCode,
            userId
        );
        if (snapshot == null) {
            throw new BusinessException("未找到对应合作单");
        }
        return snapshot;
    }

    private Long queryCooperationIdByPostId(long postId) {
        return jdbcTemplate.query(
            "select cooperation_id from post where id = ? limit 1",
            rs -> rs.next() ? (Long) rs.getObject("cooperation_id") : null,
            postId
        );
    }

    private CooperationItemVO mapCooperation(ResultSet rs) throws SQLException {
        int statusCode = rs.getInt("cooperation_status");
        Timestamp acceptedAt = rs.getTimestamp("accepted_at");
        Timestamp rewardIssuedAt = rs.getTimestamp("reward_issued_at");
        int targetPostCount = Math.max(rs.getInt("target_post_count"), 1);
        int targetLikeCount = Math.max(rs.getInt("target_like_count"), 0);
        int submittedPostCount = rs.getInt("submitted_post_count");
        int approvedPostCount = rs.getInt("approved_post_count");
        int approvedLikeCount = Math.max(rs.getInt("approved_like_count"), 0);
        boolean rewardReady = statusCode == STATUS_REWARD_READY;
        boolean rewardIssued = statusCode == STATUS_REWARD_ISSUED;
        boolean canAccept = statusCode == STATUS_PENDING;
        boolean accepted = acceptedAt != null || (statusCode != STATUS_PENDING && statusCode != STATUS_ABANDONED);
        boolean canPublish = statusCode == STATUS_RUNNING;
        boolean canAbandon = statusCode == STATUS_RUNNING;
        return new CooperationItemVO(
            rs.getString("cooperation_code"),
            rs.getLong("id"),
            rs.getString("cooperation_title"),
            rs.getString("merchant_name"),
            coalesce(rs.getString("cooperation_desc"), ""),
            mapStatusText(statusCode, acceptedAt),
            statusCode,
            rs.getBigDecimal("reward_amount"),
            targetPostCount,
            approvedPostCount,
            submittedPostCount,
            targetLikeCount,
            approvedLikeCount,
            formatDateTime(rs.getTimestamp("deadline_at")),
            canAccept,
            accepted,
            canPublish,
            canAbandon,
            rewardReady,
            rewardIssued,
            buildRuleText(targetPostCount, targetLikeCount),
            buildProgressText(statusCode, approvedPostCount, targetPostCount, approvedLikeCount, targetLikeCount, acceptedAt, rewardIssuedAt)
        );
    }

    private String buildProgressText(
        int statusCode,
        int approvedPostCount,
        int targetPostCount,
        int approvedLikeCount,
        int targetLikeCount,
        Timestamp acceptedAt,
        Timestamp rewardIssuedAt
    ) {
        if (statusCode == STATUS_REWARD_ISSUED) {
            String issuedAt = formatDateTime(rewardIssuedAt);
            return issuedAt.equals("-")
                ? "奖励已发放，可前往激励中心提现"
                : "奖励已于 " + issuedAt + " 发放，可前往激励中心提现";
        }
        if (statusCode == STATUS_ABANDONED) {
            return acceptedAt == null
                ? "该合作邀请已拒绝，可继续浏览其他合作机会"
                : "该合作单已放弃，已绑定内容不会再计入奖励结算";
        }
        if (statusCode == STATUS_REWARD_READY) {
            return targetLikeCount > 0
                ? "已达标：通过 " + approvedPostCount + "/" + targetPostCount + " 篇，累计点赞 " + approvedLikeCount + "/" + targetLikeCount
                : "合作内容已达标，等待管理员确认奖励";
        }
        if (statusCode == STATUS_PENDING) {
            return targetLikeCount > 0
                ? "接受合作后，需审核通过内容并累计点赞达标"
                : "接受合作后即可发布绑定内容";
        }
        if (targetLikeCount > 0) {
            return "已通过 " + approvedPostCount + "/" + targetPostCount + " 篇，累计点赞 " + approvedLikeCount + "/" + targetLikeCount;
        }
        return "已通过 " + approvedPostCount + "/" + targetPostCount + " 篇内容审核";
    }

    private String buildRuleText(int targetPostCount, int targetLikeCount) {
        if (targetLikeCount > 0) {
            return "需审核通过 " + targetPostCount + " 篇内容，累计获得 " + targetLikeCount + " 个赞";
        }
        return "需审核通过 " + targetPostCount + " 篇内容";
    }

    private String mapStatusText(int statusCode, Timestamp acceptedAt) {
        return switch (statusCode) {
            case STATUS_RUNNING -> "进行中";
            case STATUS_REWARD_READY -> "待发奖励";
            case STATUS_REWARD_ISSUED -> "已发奖励";
            case STATUS_ABANDONED -> acceptedAt == null ? "已拒绝" : "已放弃";
            default -> "待接受";
        };
    }

    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record CooperationSnapshot(
        Long id,
        String title,
        int statusCode,
        Timestamp acceptedAt,
        Timestamp rewardIssuedAt,
        int targetPostCount,
        int targetLikeCount,
        Timestamp abandonedAt
    ) {
    }

    private record CooperationStatusSnapshot(
        Long id,
        int statusCode,
        Timestamp acceptedAt,
        Timestamp rewardIssuedAt,
        int targetPostCount,
        int targetLikeCount
    ) {
    }

    private record CooperationProgress(
        int submittedPostCount,
        int approvedPostCount,
        int approvedLikeCount
    ) {
    }

    private record RewardReadySnapshot(
        Long id,
        Long userId,
        String title
    ) {
    }
}
