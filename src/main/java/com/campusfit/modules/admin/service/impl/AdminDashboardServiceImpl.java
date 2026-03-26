package com.campusfit.modules.admin.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.admin.dto.AdminActivitySaveRequest;
import com.campusfit.modules.admin.dto.AdminAnnouncementSaveRequest;
import com.campusfit.modules.admin.service.AdminDashboardService;
import com.campusfit.modules.admin.vo.AdminActivityItemVO;
import com.campusfit.modules.admin.vo.AdminAnnouncementItemVO;
import com.campusfit.modules.admin.vo.AdminContentAuditItemVO;
import com.campusfit.modules.admin.vo.AdminDashboardSummaryVO;
import com.campusfit.modules.admin.vo.AdminMerchantItemVO;
import com.campusfit.modules.admin.vo.AdminSettlementItemVO;
import com.campusfit.modules.admin.vo.AdminUserItemVO;
import com.campusfit.modules.admin.vo.AdminWithdrawRequestItemVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final DateTimeFormatter DISPLAY_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<DateTimeFormatter> INPUT_DATE_TIME_FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    );

    private final JdbcTemplate jdbcTemplate;

    public AdminDashboardServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AdminDashboardSummaryVO getSummary() {
        String sql = """
            select
                (select count(*) from app_user where date(created_at) = current_date()) as today_users,
                (select count(*) from post where audit_status = 0) as pending_audits,
                (select count(*) from product_link_click) as product_clicks,
                (select coalesce(sum(commission_amount), 0) from commission_record where settlement_status = 0) as estimated_commission,
                (select count(*) from campaign where status = 1) as active_campaigns
            """;
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> new AdminDashboardSummaryVO(
            rs.getInt("today_users"),
            rs.getInt("pending_audits"),
            rs.getInt("product_clicks"),
            rs.getBigDecimal("estimated_commission"),
            rs.getInt("active_campaigns")
        ));
    }

    @Override
    public List<AdminUserItemVO> listUsers() {
        String sql = """
            select
                u.id,
                u.nickname,
                u.status,
                up.school_name,
                up.grade_name,
                coalesce((select count(*) from post p where p.user_id = u.id and p.status = 1), 0) as post_count,
                coalesce((select count(*) from post_favorite pf where pf.user_id = u.id), 0) as favorite_count
            from app_user u
            left join user_profile up on up.user_id = u.id
            order by u.id asc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminUserItemVO(
            rs.getLong("id"),
            rs.getString("nickname"),
            joinSchool(rs.getString("school_name"), rs.getString("grade_name")),
            rs.getInt("post_count"),
            rs.getInt("favorite_count"),
            mapUserStatus(rs.getInt("status")),
            rs.getInt("status")
        ));
    }

    @Override
    public List<AdminContentAuditItemVO> listContentAuditItems() {
        String sql = """
            select
                p.id,
                p.title,
                u.nickname,
                p.scene_tag,
                coalesce(pl.link_status, 0) as link_status,
                p.audit_status,
                p.created_at
            from post p
            join app_user u on u.id = p.user_id
            left join product_link pl on pl.post_id = p.id
            order by case when p.audit_status = 0 then 0 else 1 end, p.created_at desc, p.id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int auditStatus = rs.getInt("audit_status");
            int linkStatus = rs.getInt("link_status");
            return new AdminContentAuditItemVO(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("nickname"),
                coalesce(rs.getString("scene_tag"), "-"),
                linkStatus == 1 ? "已配置" : "未配置",
                mapAuditStatus(auditStatus),
                auditStatus,
                formatDateTime(rs.getTimestamp("created_at"))
            );
        });
    }

    @Override
    public List<AdminAnnouncementItemVO> listAnnouncements() {
        String sql = """
            select
                id,
                title,
                badge_label,
                summary,
                content,
                publish_time,
                expire_time,
                status,
                pinned_flag,
                sort_order
            from official_announcement
            order by case when status = 1 then 0 else 1 end,
                     pinned_flag desc,
                     sort_order asc,
                     publish_time desc,
                     id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminAnnouncementItemVO(
            rs.getLong("id"),
            rs.getString("title"),
            coalesce(rs.getString("badge_label"), "官方公告"),
            rs.getString("summary"),
            coalesce(rs.getString("content"), ""),
            formatDateTime(rs.getTimestamp("publish_time")),
            formatDateTime(rs.getTimestamp("expire_time")),
            rs.getInt("status") == 1 ? "已上线" : "已下线",
            rs.getInt("status"),
            rs.getInt("pinned_flag") == 1,
            rs.getInt("sort_order")
        ));
    }

    @Override
    public List<AdminActivityItemVO> listActivities() {
        String sql = """
            select
                a.id,
                a.activity_code,
                a.title,
                a.badge_label,
                a.theme_desc,
                a.summary_desc,
                a.period_text,
                a.reward_desc,
                a.participation_desc,
                a.scene_label,
                a.status_code,
                a.featured_flag,
                a.publish_selectable_flag,
                a.heat_value,
                a.sort_order,
                a.status,
                a.start_time,
                a.end_time,
                coalesce(entry_stats.entry_count, 0) as entry_count
            from activity_topic a
            left join (
                select pa.activity_id, count(*) as entry_count
                from post_activity_binding pa
                join post p on p.id = pa.post_id
                where p.status = 1 and p.audit_status = 1
                group by pa.activity_id
            ) entry_stats on entry_stats.activity_id = a.id
            order by case when a.status = 1 then 0 else 1 end,
                     a.sort_order asc,
                     a.id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            boolean active = rs.getInt("status") == 1;
            String statusCode = coalesce(rs.getString("status_code"), "ONGOING");
            return new AdminActivityItemVO(
                rs.getLong("id"),
                rs.getString("activity_code"),
                rs.getString("title"),
                coalesce(rs.getString("badge_label"), "热门活动"),
                coalesce(rs.getString("theme_desc"), ""),
                coalesce(rs.getString("summary_desc"), ""),
                coalesce(rs.getString("period_text"), ""),
                coalesce(rs.getString("reward_desc"), ""),
                coalesce(rs.getString("participation_desc"), ""),
                coalesce(rs.getString("scene_label"), ""),
                mapActivityStatus(active, statusCode),
                statusCode,
                rs.getInt("featured_flag") == 1,
                rs.getInt("publish_selectable_flag") == 1,
                rs.getInt("heat_value"),
                rs.getInt("entry_count"),
                rs.getInt("sort_order"),
                active,
                formatDateTime(rs.getTimestamp("start_time")),
                formatDateTime(rs.getTimestamp("end_time"))
            );
        });
    }

    @Override
    public List<AdminMerchantItemVO> listMerchants() {
        String sql = """
            select
                m.id,
                m.merchant_name,
                coalesce(m.contact_name, '-') as contact_name,
                coalesce((select count(*) from campaign c where c.merchant_id = m.id), 0) as campaign_count,
                case when m.cooperation_status = 1 then '合作中' else '意向商家' end as cooperation_status
            from merchant m
            order by m.id asc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminMerchantItemVO(
            rs.getLong("id"),
            rs.getString("merchant_name"),
            rs.getString("contact_name"),
            rs.getInt("campaign_count"),
            rs.getString("cooperation_status")
        ));
    }

    @Override
    public List<AdminSettlementItemVO> listSettlements() {
        String sql = """
            select
                cr.id,
                u.nickname,
                cr.income_type,
                cr.commission_amount,
                cr.settlement_status,
                cr.created_at
            from commission_record cr
            join app_user u on u.id = cr.user_id
            order by cr.created_at desc, cr.id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int settlementStatus = rs.getInt("settlement_status");
            return new AdminSettlementItemVO(
                rs.getLong("id"),
                rs.getString("nickname"),
                normalizeSettlementType(rs.getString("income_type")),
                scaleAmount(rs.getBigDecimal("commission_amount")),
                settlementStatus == 1 ? "已结算" : "待结算",
                settlementStatus,
                formatDateTime(rs.getTimestamp("created_at"))
            );
        });
    }

    @Override
    public List<AdminWithdrawRequestItemVO> listWithdrawRequests() {
        String sql = """
            select
                cwr.id,
                u.nickname,
                cwr.request_amount,
                cwr.request_status,
                cwr.created_at,
                cwr.processed_at,
                cwr.remark
            from creator_withdraw_request cwr
            join app_user u on u.id = cwr.user_id
            order by case when cwr.request_status = 0 then 0 else 1 end, cwr.created_at desc, cwr.id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AdminWithdrawRequestItemVO(
            rs.getLong("id"),
            rs.getString("nickname"),
            scaleAmount(rs.getBigDecimal("request_amount")),
            mapWithdrawStatus(rs.getInt("request_status")),
            rs.getInt("request_status"),
            formatDateTime(rs.getTimestamp("created_at")),
            formatDateTime(rs.getTimestamp("processed_at")),
            coalesce(rs.getString("remark"), rs.getInt("request_status") == 0 ? "等待财务审核" : "-")
        ));
    }

    @Override
    public void freezeUser(Long userId) {
        requireAffected(jdbcTemplate.update("update app_user set status = 0 where id = ?", userId), "用户不存在");
    }

    @Override
    public void unfreezeUser(Long userId) {
        requireAffected(jdbcTemplate.update("update app_user set status = 1 where id = ?", userId), "用户不存在");
    }

    @Override
    @Transactional
    public void approvePost(Long postId) {
        PostAuditSnapshot snapshot = requirePostAuditSnapshot(postId);
        requireAffected(
            jdbcTemplate.update("update post set audit_status = 1, status = 1, updated_at = now() where id = ?", postId),
            "内容不存在"
        );
        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            snapshot.userId(),
            "系统通知",
            "你的穿搭已通过审核",
            "你提交的《" + snapshot.title() + "》已经通过审核，现在会展示到前台内容流中。"
        );
    }

    @Override
    @Transactional
    public void rejectPost(Long postId) {
        PostAuditSnapshot snapshot = requirePostAuditSnapshot(postId);
        requireAffected(
            jdbcTemplate.update("update post set audit_status = 2, status = 0, updated_at = now() where id = ?", postId),
            "内容不存在"
        );
        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            snapshot.userId(),
            "系统通知",
            "你的穿搭未通过审核",
            "你提交的《" + snapshot.title() + "》暂未通过审核，请修改后重新提交。"
        );
    }

    @Override
    @Transactional
    public void createAnnouncement(AdminAnnouncementSaveRequest request, String operatorName) {
        AnnouncementPayload payload = normalizeAnnouncementPayload(request);
        jdbcTemplate.update(
            """
            insert into official_announcement (
                title, badge_label, summary, content, status, pinned_flag, sort_order,
                publish_time, expire_time, created_by, updated_by, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
            payload.title(),
            payload.badgeLabel(),
            payload.summary(),
            payload.content(),
            payload.status(),
            payload.pinnedFlag(),
            payload.sortOrder(),
            Timestamp.valueOf(payload.publishTime()),
            toTimestamp(payload.expireTime()),
            normalizeOperator(operatorName),
            normalizeOperator(operatorName)
        );
        if (isAnnouncementVisibleNow(payload.status(), payload.publishTime(), payload.expireTime())) {
            broadcastAnnouncementPublished(payload.title());
        }
    }

    @Override
    @Transactional
    public void updateAnnouncement(Long announcementId, AdminAnnouncementSaveRequest request, String operatorName) {
        AnnouncementPayload payload = normalizeAnnouncementPayload(request);
        requireAffected(
            jdbcTemplate.update(
                """
                update official_announcement
                set title = ?, badge_label = ?, summary = ?, content = ?, status = ?, pinned_flag = ?, sort_order = ?,
                    publish_time = ?, expire_time = ?, updated_by = ?, updated_at = now()
                where id = ?
                """,
                payload.title(),
                payload.badgeLabel(),
                payload.summary(),
                payload.content(),
                payload.status(),
                payload.pinnedFlag(),
                payload.sortOrder(),
                Timestamp.valueOf(payload.publishTime()),
                toTimestamp(payload.expireTime()),
                normalizeOperator(operatorName),
                announcementId
            ),
            "公告不存在"
        );
    }

    @Override
    public void enableAnnouncement(Long announcementId, String operatorName) {
        AnnouncementNoticeSnapshot snapshot = requireAnnouncementNoticeSnapshot(announcementId);
        requireAffected(
            jdbcTemplate.update(
                "update official_announcement set status = 1, updated_by = ?, updated_at = now() where id = ?",
                normalizeOperator(operatorName),
                announcementId
            ),
            "公告不存在"
        );
        if (isAnnouncementVisibleNow(1, snapshot.publishTime(), snapshot.expireTime())) {
            broadcastAnnouncementPublished(snapshot.title());
        }
    }

    @Override
    public void disableAnnouncement(Long announcementId, String operatorName) {
        requireAffected(
            jdbcTemplate.update(
                "update official_announcement set status = 0, updated_by = ?, updated_at = now() where id = ?",
                normalizeOperator(operatorName),
                announcementId
            ),
            "公告不存在"
        );
    }

    @Override
    @Transactional
    public void createActivity(AdminActivitySaveRequest request) {
        ActivityPayload payload = normalizeActivityPayload(request);
        String activityCode = generateActivityCode(payload.title());
        jdbcTemplate.update(
            """
            insert into activity_topic (
                activity_code, title, badge_label, theme_desc, summary_desc, period_text,
                reward_desc, participation_desc, scene_label, status_code, featured_flag,
                publish_selectable_flag,
                heat_value, sort_order, status, start_time, end_time, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
            activityCode,
            payload.title(),
            payload.badgeLabel(),
            payload.themeDesc(),
            payload.summaryDesc(),
            payload.periodText(),
            payload.rewardDesc(),
            payload.participationDesc(),
            payload.sceneLabel(),
            payload.statusCode(),
            payload.featuredFlag(),
            payload.publishSelectableFlag(),
            payload.heatValue(),
            payload.sortOrder(),
            payload.status(),
            toTimestamp(payload.startTime()),
            toTimestamp(payload.endTime())
        );
        if (payload.status() == 1) {
            broadcastActivityPublished(payload.title());
        }
    }

    @Override
    @Transactional
    public void updateActivity(Long activityId, AdminActivitySaveRequest request) {
        ActivityPayload payload = normalizeActivityPayload(request);
        requireAffected(
            jdbcTemplate.update(
                """
                update activity_topic
                set title = ?, badge_label = ?, theme_desc = ?, summary_desc = ?, period_text = ?,
                    reward_desc = ?, participation_desc = ?, scene_label = ?, status_code = ?,
                    featured_flag = ?, publish_selectable_flag = ?, heat_value = ?, sort_order = ?, status = ?, start_time = ?,
                    end_time = ?, updated_at = now()
                where id = ?
                """,
                payload.title(),
                payload.badgeLabel(),
                payload.themeDesc(),
                payload.summaryDesc(),
                payload.periodText(),
                payload.rewardDesc(),
                payload.participationDesc(),
                payload.sceneLabel(),
                payload.statusCode(),
                payload.featuredFlag(),
                payload.publishSelectableFlag(),
                payload.heatValue(),
                payload.sortOrder(),
                payload.status(),
                toTimestamp(payload.startTime()),
                toTimestamp(payload.endTime()),
                activityId
            ),
            "活动不存在"
        );
    }

    @Override
    public void startActivity(Long activityId) {
        ActivityNoticeSnapshot snapshot = requireActivityNoticeSnapshot(activityId);
        requireAffected(
            jdbcTemplate.update(
                """
                update activity_topic
                set status = 1,
                    status_code = case when status_code = 'RECRUITING' then 'RECRUITING' else 'ONGOING' end,
                    updated_at = now()
                where id = ?
                """,
                activityId
            ),
            "活动不存在"
        );
        broadcastActivityPublished(snapshot.title());
    }

    @Override
    public void stopActivity(Long activityId) {
        requireAffected(
            jdbcTemplate.update(
                """
                update activity_topic
                set status = 0,
                    status_code = 'FINISHED',
                    featured_flag = 0,
                    updated_at = now()
                where id = ?
                """,
                activityId
            ),
            "活动不存在"
        );
    }

    @Override
    public void settleCommission(Long recordId) {
        requireAffected(
            jdbcTemplate.update("update commission_record set settlement_status = 1 where id = ?", recordId),
            "结算记录不存在"
        );
    }

    @Override
    @Transactional
    public void approveWithdrawRequest(Long requestId) {
        WithdrawRequestSnapshot request = requirePendingWithdrawRequest(requestId);
        String remark = "财务已确认打款，请提醒创作者注意查收。";
        requireAffected(
            jdbcTemplate.update(
                "update creator_withdraw_request set request_status = 1, processed_at = now(), remark = ? where id = ? and request_status = 0",
                remark,
                requestId
            ),
            "提现申请不存在或已处理"
        );
        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            request.userId(),
            "激励通知",
            "提现申请已通过",
            "你的 " + formatCurrency(request.amount()) + " 创作激励提现申请已通过，平台已安排打款，请注意查收。"
        );
    }

    @Override
    @Transactional
    public void rejectWithdrawRequest(Long requestId) {
        WithdrawRequestSnapshot request = requirePendingWithdrawRequest(requestId);
        String remark = "平台已驳回本次提现申请，请核对收款信息后重新提交。";
        requireAffected(
            jdbcTemplate.update(
                "update creator_withdraw_request set request_status = 2, processed_at = now(), remark = ? where id = ? and request_status = 0",
                remark,
                requestId
            ),
            "提现申请不存在或已处理"
        );
        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            request.userId(),
            "激励通知",
            "提现申请未通过",
            "你的 " + formatCurrency(request.amount()) + " 创作激励提现申请未通过，请核对收款信息后重新提交。"
        );
    }

    private void requireAffected(int affectedRows, String message) {
        if (affectedRows <= 0) {
            throw new BusinessException(message);
        }
    }

    private PostAuditSnapshot requirePostAuditSnapshot(Long postId) {
        PostAuditSnapshot snapshot = jdbcTemplate.query(
            """
            select id, user_id, title
            from post
            where id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new PostAuditSnapshot(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("title")
                )
                : null,
            postId
        );
        if (snapshot == null) {
            throw new BusinessException("内容不存在");
        }
        return snapshot;
    }

    private WithdrawRequestSnapshot requirePendingWithdrawRequest(Long requestId) {
        WithdrawRequestSnapshot request = jdbcTemplate.query(
            """
            select id, user_id, request_amount, request_status
            from creator_withdraw_request
            where id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new WithdrawRequestSnapshot(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    scaleAmount(rs.getBigDecimal("request_amount")),
                    rs.getInt("request_status")
                )
                : null,
            requestId
        );
        if (request == null) {
            throw new BusinessException("提现申请不存在");
        }
        if (request.statusCode() != 0) {
            throw new BusinessException("该提现申请已处理");
        }
        return request;
    }

    private AnnouncementNoticeSnapshot requireAnnouncementNoticeSnapshot(Long announcementId) {
        AnnouncementNoticeSnapshot snapshot = jdbcTemplate.query(
            """
            select id, title, publish_time, expire_time
            from official_announcement
            where id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new AnnouncementNoticeSnapshot(
                    rs.getLong("id"),
                    rs.getString("title"),
                    toLocalDateTime(rs.getTimestamp("publish_time")),
                    toLocalDateTime(rs.getTimestamp("expire_time"))
                )
                : null,
            announcementId
        );
        if (snapshot == null) {
            throw new BusinessException("公告不存在");
        }
        return snapshot;
    }

    private ActivityNoticeSnapshot requireActivityNoticeSnapshot(Long activityId) {
        ActivityNoticeSnapshot snapshot = jdbcTemplate.query(
            """
            select id, title
            from activity_topic
            where id = ?
            limit 1
            """,
            rs -> rs.next()
                ? new ActivityNoticeSnapshot(
                    rs.getLong("id"),
                    rs.getString("title")
                )
                : null,
            activityId
        );
        if (snapshot == null) {
            throw new BusinessException("活动不存在");
        }
        return snapshot;
    }

    private boolean isAnnouncementVisibleNow(int status, LocalDateTime publishTime, LocalDateTime expireTime) {
        if (status != 1) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (publishTime != null && publishTime.isAfter(now)) {
            return false;
        }
        return expireTime == null || !expireTime.isBefore(now);
    }

    private void broadcastAnnouncementPublished(String title) {
        broadcastMessageToAllUsers(
            "官方公告",
            "官方发布了新公告",
            "官方公告《" + title + "》已上线，打开首页官方公告卡片即可查看完整内容。"
        );
    }

    private void broadcastActivityPublished(String title) {
        broadcastMessageToAllUsers(
            "活动通知",
            "官方发起了新活动",
            "新活动《" + title + "》已上线，进入活动中心即可查看规则、奖励和参与方式。"
        );
    }

    private void broadcastMessageToAllUsers(String messageType, String title, String content) {
        jdbcTemplate.update(
            """
            insert into message_notification (user_id, message_type, title, content, read_status, created_at)
            select id, ?, ?, ?, 0, now()
            from app_user
            where status = 1
            """,
            messageType,
            title,
            content
        );
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private AnnouncementPayload normalizeAnnouncementPayload(AdminAnnouncementSaveRequest request) {
        if (request == null) {
            throw new BusinessException("公告内容不能为空");
        }
        String title = normalizeRequiredText(request.title(), "公告标题不能为空");
        String badgeLabel = coalesce(normalizeOptionalText(request.badgeLabel()), "官方公告");
        String summary = normalizeRequiredText(request.summary(), "公告摘要不能为空");
        String content = coalesce(normalizeOptionalText(request.content()), summary);
        int status = normalizeFlag(request.status(), 1);
        int pinnedFlag = normalizeFlag(request.pinnedFlag(), 1);
        int sortOrder = normalizeNonNegative(request.sortOrder(), 0, "公告排序不能为负数");
        LocalDateTime publishTime = parseDateTime(request.publishTime(), LocalDateTime.now(), "发布时间格式不正确");
        LocalDateTime expireTime = parseDateTime(request.expireTime(), null, "结束时间格式不正确");
        if (expireTime != null && expireTime.isBefore(publishTime)) {
            throw new BusinessException("结束时间不能早于发布时间");
        }
        return new AnnouncementPayload(title, badgeLabel, summary, content, status, pinnedFlag, sortOrder, publishTime, expireTime);
    }

    private ActivityPayload normalizeActivityPayload(AdminActivitySaveRequest request) {
        if (request == null) {
            throw new BusinessException("活动内容不能为空");
        }
        String title = normalizeRequiredText(request.title(), "活动标题不能为空");
        String badgeLabel = coalesce(normalizeOptionalText(request.badgeLabel()), "热门活动");
        String themeDesc = normalizeRequiredText(request.themeDesc(), "活动主题说明不能为空");
        String summaryDesc = normalizeRequiredText(request.summaryDesc(), "活动摘要不能为空");
        String periodText = normalizeRequiredText(request.periodText(), "活动时间文案不能为空");
        String rewardDesc = normalizeRequiredText(request.rewardDesc(), "奖励说明不能为空");
        String participationDesc = normalizeRequiredText(request.participationDesc(), "参与说明不能为空");
        String sceneLabel = normalizeRequiredText(request.sceneLabel(), "活动场景不能为空");
        int status = normalizeFlag(request.status(), 1);
        String statusCode = normalizeActivityStatusCode(request.statusCode(), status);
        int featuredFlag = status == 0 ? 0 : normalizeFlag(request.featuredFlag(), 0);
        int publishSelectableFlag = normalizeFlag(request.publishSelectableFlag(), 1);
        int heatValue = normalizeNonNegative(request.heatValue(), 0, "热度值不能为负数");
        int sortOrder = normalizeNonNegative(request.sortOrder(), 0, "活动排序不能为负数");
        LocalDateTime startTime = parseDateTime(request.startTime(), null, "活动开始时间格式不正确");
        LocalDateTime endTime = parseDateTime(request.endTime(), null, "活动结束时间格式不正确");
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new BusinessException("活动结束时间不能早于开始时间");
        }
        return new ActivityPayload(
            title,
            badgeLabel,
            themeDesc,
            summaryDesc,
            periodText,
            rewardDesc,
            participationDesc,
            sceneLabel,
            statusCode,
            featuredFlag,
            publishSelectableFlag,
            heatValue,
            sortOrder,
            status,
            startTime,
            endTime
        );
    }

    private String generateActivityCode(String title) {
        String baseCode = title == null
            ? "activity"
            : title.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (baseCode.isBlank()) {
            baseCode = "activity";
        }
        String candidate = baseCode;
        int suffix = 2;
        while (activityCodeExists(candidate)) {
            candidate = baseCode + "-" + suffix;
            suffix += 1;
        }
        return candidate;
    }

    private boolean activityCodeExists(String activityCode) {
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from activity_topic where activity_code = ?",
            Integer.class,
            activityCode
        );
        return count != null && count > 0;
    }

    private String joinSchool(String schoolName, String gradeName) {
        if (schoolName == null || schoolName.isBlank()) {
            return gradeName == null || gradeName.isBlank() ? "校园用户" : gradeName;
        }
        if (gradeName == null || gradeName.isBlank()) {
            return schoolName;
        }
        return schoolName + " / " + gradeName;
    }

    private String mapUserStatus(int status) {
        return status == 1 ? "正常" : "已冻结";
    }

    private String mapAuditStatus(int auditStatus) {
        return switch (auditStatus) {
            case 1 -> "已通过";
            case 2 -> "已驳回";
            default -> "待审核";
        };
    }

    private String mapActivityStatus(boolean active, String statusCode) {
        if (!active) {
            return "已终止";
        }
        if ("RECRUITING".equalsIgnoreCase(statusCode)) {
            return "征集中";
        }
        if ("FINISHED".equalsIgnoreCase(statusCode)) {
            return "已结束";
        }
        return "进行中";
    }

    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return timestamp.toLocalDateTime().format(DISPLAY_DATE_TIME_FORMATTER);
    }

    private Timestamp toTimestamp(LocalDateTime dateTime) {
        return dateTime == null ? null : Timestamp.valueOf(dateTime);
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal amount) {
        return "￥" + scaleAmount(amount).toPlainString();
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

    private String normalizeActivityStatusCode(String value, int status) {
        if (status == 0) {
            return "FINISHED";
        }
        String normalized = coalesce(normalizeOptionalText(value), "ONGOING").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "RECRUITING", "ONGOING", "FINISHED" -> normalized;
            default -> "ONGOING";
        };
    }

    private LocalDateTime parseDateTime(String value, LocalDateTime fallback, String errorMessage) {
        String normalized = normalizeOptionalText(value);
        if (normalized == null) {
            return fallback;
        }
        for (DateTimeFormatter formatter : INPUT_DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException ignored) {
                // try next formatter
            }
        }
        throw new BusinessException(errorMessage);
    }

    private int normalizeFlag(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value == 0 ? 0 : 1;
    }

    private int normalizeNonNegative(Integer value, int defaultValue, String errorMessage) {
        int normalized = value == null ? defaultValue : value;
        if (normalized < 0) {
            throw new BusinessException(errorMessage);
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

    private String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeOperator(String operatorName) {
        return coalesce(normalizeOptionalText(operatorName), "系统运营");
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record AnnouncementNoticeSnapshot(
        Long announcementId,
        String title,
        LocalDateTime publishTime,
        LocalDateTime expireTime
    ) {
    }

    private record ActivityNoticeSnapshot(
        Long activityId,
        String title
    ) {
    }

    private record WithdrawRequestSnapshot(
        Long requestId,
        Long userId,
        BigDecimal amount,
        int statusCode
    ) {
    }

    private record PostAuditSnapshot(
        Long postId,
        Long userId,
        String title
    ) {
    }

    private record AnnouncementPayload(
        String title,
        String badgeLabel,
        String summary,
        String content,
        int status,
        int pinnedFlag,
        int sortOrder,
        LocalDateTime publishTime,
        LocalDateTime expireTime
    ) {
    }

    private record ActivityPayload(
        String title,
        String badgeLabel,
        String themeDesc,
        String summaryDesc,
        String periodText,
        String rewardDesc,
        String participationDesc,
        String sceneLabel,
        String statusCode,
        int featuredFlag,
        int publishSelectableFlag,
        int heatValue,
        int sortOrder,
        int status,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
    }
}
