package com.campusfit.modules.activity.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.activity.service.ActivityService;
import com.campusfit.modules.activity.vo.ActivityItemVO;
import com.campusfit.modules.activity.vo.ActivitySummaryVO;
import com.campusfit.modules.auth.support.UserAuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ActivityServiceImpl implements ActivityService {

    private static final String ACTIVITY_SELECT = """
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
            a.publish_selectable_flag,
            a.heat_value,
            coalesce(entry_stats.entry_count, 0) as entry_count,
            case
                when ? > 0 and exists(
                    select 1
                    from user_activity_join ua
                    where ua.activity_id = a.id and ua.user_id = ?
                ) then true
                else false
            end as joined
        from activity_topic a
        left join (
            select pa.activity_id, count(*) as entry_count
            from post_activity_binding pa
            join post p on p.id = pa.post_id
            where p.status = 1 and p.audit_status = 1
            group by pa.activity_id
        ) entry_stats on entry_stats.activity_id = a.id
        where a.status = 1
        """;

    private final JdbcTemplate jdbcTemplate;

    public ActivityServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ActivityItemVO> listActivities() {
        return queryActivities(" order by a.sort_order asc, a.id asc", List.of(), UserAuthContext.getCurrentUserId());
    }

    @Override
    public List<ActivityItemVO> listFeaturedActivities() {
        return queryActivities(
            " and a.featured_flag = 1 order by a.sort_order asc, a.id asc limit 2",
            List.of(),
            UserAuthContext.getCurrentUserId()
        );
    }

    @Override
    public List<ActivityItemVO> listMyActivities() {
        long currentUserId = UserAuthContext.requireUserId();
        return queryActivities(
            """
             and exists (
                 select 1
                 from user_activity_join ua
                 where ua.activity_id = a.id and ua.user_id = ?
             )
             order by a.sort_order asc, a.id asc
            """,
            List.of(currentUserId),
            currentUserId
        );
    }

    @Override
    public ActivitySummaryVO getMySummary() {
        long currentUserId = UserAuthContext.requireUserId();
        Integer joinedCount = jdbcTemplate.queryForObject(
            "select count(*) from user_activity_join where user_id = ?",
            Integer.class,
            currentUserId
        );
        Integer ongoingCount = jdbcTemplate.queryForObject(
            """
            select count(*)
            from user_activity_join ua
            join activity_topic a on a.id = ua.activity_id
            where ua.user_id = ?
              and a.status = 1
              and a.status_code in ('ONGOING', 'RECRUITING')
            """,
            Integer.class,
            currentUserId
        );
        return new ActivitySummaryVO(
            joinedCount == null ? 0 : joinedCount,
            ongoingCount == null ? 0 : ongoingCount
        );
    }

    @Override
    @Transactional
    public ActivityItemVO toggleJoin(String activityCode) {
        long currentUserId = UserAuthContext.requireUserId();
        long activityId = resolveActivityId(activityCode);
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from user_activity_join where activity_id = ? and user_id = ?",
            Integer.class,
            activityId,
            currentUserId
        );
        if (exists != null && exists > 0) {
            jdbcTemplate.update(
                "delete from user_activity_join where activity_id = ? and user_id = ?",
                activityId,
                currentUserId
            );
        } else {
            jdbcTemplate.update(
                "insert into user_activity_join (activity_id, user_id, created_at) values (?, ?, now())",
                activityId,
                currentUserId
            );
        }
        return findByCode(activityCode);
    }

    @Override
    public ActivityItemVO findByPostCode(String postCode) {
        if (postCode == null || postCode.isBlank()) {
            return null;
        }
        Long currentUserId = UserAuthContext.getCurrentUserId();
        List<ActivityItemVO> activities = queryActivities(
            """
             and exists (
                 select 1
                 from post_activity_binding pa
                 join post p on p.id = pa.post_id
                 where pa.activity_id = a.id and p.post_code = ?
             )
             limit 1
            """,
            List.of(postCode),
            currentUserId
        );
        return activities.isEmpty() ? null : activities.get(0);
    }

    @Override
    public ActivityItemVO findByCode(String activityCode) {
        if (activityCode == null || activityCode.isBlank()) {
            return null;
        }
        Long currentUserId = UserAuthContext.getCurrentUserId();
        List<ActivityItemVO> activities = queryActivities(
            " and a.activity_code = ? limit 1",
            List.of(activityCode),
            currentUserId
        );
        return activities.isEmpty() ? null : activities.get(0);
    }

    @Override
    @Transactional
    public void bindPostToActivity(long postId, long userId, String activityCode) {
        jdbcTemplate.update("delete from post_activity_binding where post_id = ?", postId);
        if (activityCode == null || activityCode.isBlank()) {
            return;
        }
        requirePublishSelectableActivity(activityCode);
        long activityId = resolveActivityId(activityCode);
        Integer joinExists = jdbcTemplate.queryForObject(
            "select count(*) from user_activity_join where activity_id = ? and user_id = ?",
            Integer.class,
            activityId,
            userId
        );
        if (joinExists == null || joinExists == 0) {
            jdbcTemplate.update(
                "insert into user_activity_join (activity_id, user_id, created_at) values (?, ?, now())",
                activityId,
                userId
            );
        }
        jdbcTemplate.update(
            "insert into post_activity_binding (post_id, activity_id, created_at, updated_at) values (?, ?, now(), now())",
            postId,
            activityId
        );
    }

    private List<ActivityItemVO> queryActivities(String suffix, List<Object> extraParams, Long currentUserId) {
        long viewerUserId = currentUserId == null ? -1L : currentUserId;
        List<Object> params = new ArrayList<>();
        params.add(viewerUserId);
        params.add(viewerUserId);
        params.addAll(extraParams);
        return jdbcTemplate.query(
            ACTIVITY_SELECT + suffix,
            (rs, rowNum) -> mapActivity(rs),
            params.toArray()
        );
    }

    private long resolveActivityId(String activityCode) {
        Long activityId = jdbcTemplate.query(
            "select id from activity_topic where activity_code = ? and status = 1 limit 1",
            (rs, rowNum) -> rs.getLong("id"),
            activityCode
        ).stream().findFirst().orElse(null);
        if (activityId == null) {
            throw new BusinessException("未找到对应活动");
        }
        return activityId;
    }

    private ActivityItemVO requirePublishSelectableActivity(String activityCode) {
        ActivityItemVO activity = findByCode(activityCode);
        if (activity == null) {
            throw new BusinessException("鏈壘鍒板搴旀椿鍔?");
        }
        if (!activity.selectable()) {
            throw new BusinessException("褰撳墠娲诲姩涓嶆敮鎸佸湪鍙戝竷鏃堕€夋嫨");
        }
        return activity;
    }

    private ActivityItemVO mapActivity(ResultSet rs) throws SQLException {
        boolean joined = rs.getBoolean("joined");
        boolean selectable = rs.getInt("publish_selectable_flag") == 1;
        int heat = rs.getInt("heat_value");
        int entries = rs.getInt("entry_count");
        String statusCopy = selectable
            ? (joined ? "你已加入活动，可以继续围绕这个专题发布内容" : "报名后可获得更稳定的活动曝光与参与进度")
            : "该活动当前仅在活动中心展示，发布时不会出现在可选列表中";
        return new ActivityItemVO(
            rs.getString("activity_code"),
            rs.getString("title"),
            rs.getString("badge_label"),
            rs.getString("theme_desc"),
            rs.getString("summary_desc"),
            rs.getString("period_text"),
            rs.getString("reward_desc"),
            rs.getString("participation_desc"),
            rs.getString("scene_label"),
            resolveStatusText(rs.getString("status_code")),
            selectable,
            heat,
            entries,
            joined,
            statusCopy,
            "热度 " + heat + " · 已有 " + entries + " 条内容"
        );
    }

    private String resolveStatusText(String statusCode) {
        if ("RECRUITING".equalsIgnoreCase(statusCode)) {
            return "招募中";
        }
        if ("FINISHED".equalsIgnoreCase(statusCode)) {
            return "已结束";
        }
        return "进行中";
    }
}
