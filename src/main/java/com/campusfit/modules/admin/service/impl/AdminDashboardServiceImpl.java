package com.campusfit.modules.admin.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.admin.service.AdminDashboardService;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
            order by p.created_at desc, p.id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int auditStatus = rs.getInt("audit_status");
            int linkStatus = rs.getInt("link_status");
            return new AdminContentAuditItemVO(
                rs.getLong("id"),
                rs.getString("title"),
                rs.getString("nickname"),
                rs.getString("scene_tag"),
                linkStatus == 1 ? "已校验" : "待校验",
                mapAuditStatus(auditStatus),
                auditStatus,
                formatDateTime(rs.getTimestamp("created_at"))
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
    public void approvePost(Long postId) {
        requireAffected(jdbcTemplate.update("update post set audit_status = 1, status = 1 where id = ?", postId), "内容不存在");
    }

    @Override
    public void rejectPost(Long postId) {
        requireAffected(jdbcTemplate.update("update post set audit_status = 2, status = 0 where id = ?", postId), "内容不存在");
    }

    @Override
    public void settleCommission(Long recordId) {
        requireAffected(jdbcTemplate.update("update commission_record set settlement_status = 1 where id = ?", recordId), "结算记录不存在");
    }

    @Override
    @Transactional
    public void approveWithdrawRequest(Long requestId) {
        WithdrawRequestSnapshot request = requirePendingWithdrawRequest(requestId);
        String remark = "财务已确认打款，请提醒创作者查收。";
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

    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private BigDecimal scaleAmount(BigDecimal amount) {
        if (amount == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String formatCurrency(BigDecimal amount) {
        return "¥" + scaleAmount(amount).toPlainString();
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

    private WithdrawRequestSnapshot requirePendingWithdrawRequest(Long requestId) {
        WithdrawRequestSnapshot request = jdbcTemplate.query("""
            select id, user_id, request_amount, request_status
            from creator_withdraw_request
            where id = ?
            limit 1
            """, rs -> rs.next()
            ? new WithdrawRequestSnapshot(
                rs.getLong("id"),
                rs.getLong("user_id"),
                scaleAmount(rs.getBigDecimal("request_amount")),
                rs.getInt("request_status")
            )
            : null, requestId);
        if (request == null) {
            throw new BusinessException("提现申请不存在");
        }
        if (request.statusCode() != 0) {
            throw new BusinessException("该提现申请已处理");
        }
        return request;
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private record WithdrawRequestSnapshot(
        Long requestId,
        Long userId,
        BigDecimal amount,
        int statusCode
    ) {
    }
}
