package com.campusfit.modules.admin.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.admin.service.AdminDashboardService;
import com.campusfit.modules.admin.vo.AdminContentAuditItemVO;
import com.campusfit.modules.admin.vo.AdminDashboardSummaryVO;
import com.campusfit.modules.admin.vo.AdminMerchantItemVO;
import com.campusfit.modules.admin.vo.AdminSettlementItemVO;
import com.campusfit.modules.admin.vo.AdminUserItemVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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
                (select coalesce(sum(favorite_count + share_count), 0) from post where status = 1 and audit_status = 1) as product_clicks,
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
                linkStatus == 1 ? "Verified" : "Pending",
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
                case when m.cooperation_status = 1 then 'Cooperating' else 'Lead' end as cooperation_status
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
                rs.getString("income_type"),
                scaleAmount(rs.getBigDecimal("commission_amount")),
                settlementStatus == 1 ? "Settled" : "Pending",
                settlementStatus,
                formatDateTime(rs.getTimestamp("created_at"))
            );
        });
    }

    @Override
    public void freezeUser(Long userId) {
        requireAffected(jdbcTemplate.update("update app_user set status = 0 where id = ?", userId), "User not found");
    }

    @Override
    public void unfreezeUser(Long userId) {
        requireAffected(jdbcTemplate.update("update app_user set status = 1 where id = ?", userId), "User not found");
    }

    @Override
    public void approvePost(Long postId) {
        requireAffected(jdbcTemplate.update("update post set audit_status = 1, status = 1 where id = ?", postId), "Post not found");
    }

    @Override
    public void rejectPost(Long postId) {
        requireAffected(jdbcTemplate.update("update post set audit_status = 2, status = 0 where id = ?", postId), "Post not found");
    }

    @Override
    public void settleCommission(Long recordId) {
        requireAffected(jdbcTemplate.update("update commission_record set settlement_status = 1 where id = ?", recordId), "Settlement record not found");
    }

    private void requireAffected(int affectedRows, String message) {
        if (affectedRows <= 0) {
            throw new BusinessException(message);
        }
    }

    private String joinSchool(String schoolName, String gradeName) {
        if (schoolName == null || schoolName.isBlank()) {
            return gradeName == null || gradeName.isBlank() ? "Campus User" : gradeName;
        }
        if (gradeName == null || gradeName.isBlank()) {
            return schoolName;
        }
        return schoolName + " - " + gradeName;
    }

    private String mapUserStatus(int status) {
        return status == 1 ? "Active" : "Frozen";
    }

    private String mapAuditStatus(int auditStatus) {
        return switch (auditStatus) {
            case 1 -> "Approved";
            case 2 -> "Rejected";
            default -> "Pending";
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
}