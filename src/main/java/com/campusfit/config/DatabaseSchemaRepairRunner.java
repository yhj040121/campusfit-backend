package com.campusfit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class DatabaseSchemaRepairRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaRepairRunner.class);

    private static final String NEW_STAR_ACTIVITY_CODE = "campus-new-star-plan";
    private static final String MERCHANT_ACTIVITY_CODE = "first-merchant-support-plan";

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaRepairRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureAppUserPasswordHashColumn();
        ensurePostDraftColumns();
        ensurePostCommentColumns();
        ensurePostCommentLikeTable();
        syncDefaultActivities();
        syncDefaultAnnouncement();
    }

    private void ensureAppUserPasswordHashColumn() {
        Integer columnCount = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 'app_user'
              and column_name = 'password_hash'
            """,
            Integer.class
        );
        if (columnCount != null && columnCount > 0) {
            return;
        }

        jdbcTemplate.execute("alter table app_user add column password_hash varchar(255) null after avatar_url");
        jdbcTemplate.update("update app_user set password_hash = null where password_hash = ''");
        log.info("Added missing app_user.password_hash column automatically.");
    }

    private void ensurePostDraftColumns() {
        ensurePostDraftColumn(
            "product_price",
            "alter table post_draft add column product_price decimal(10,2) null after product_link"
        );
        ensurePostDraftColumn(
            "activity_code",
            "alter table post_draft add column activity_code varchar(50) null after product_price"
        );
    }

    private void ensurePostDraftColumn(String columnName, String alterSql) {
        Integer columnCount = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 'post_draft'
              and column_name = ?
            """,
            Integer.class,
            columnName
        );
        if (columnCount != null && columnCount > 0) {
            return;
        }

        jdbcTemplate.execute(alterSql);
        log.info("Added missing post_draft.{} column automatically.", columnName);
    }

    private void ensurePostCommentColumns() {
        ensurePostCommentColumn(
            "parent_comment_id",
            "alter table post_comment add column parent_comment_id bigint null after created_at"
        );
        ensurePostCommentColumn(
            "reply_user_id",
            "alter table post_comment add column reply_user_id bigint null after parent_comment_id"
        );
    }

    private void ensurePostCommentColumn(String columnName, String alterSql) {
        Integer columnCount = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.columns
            where table_schema = database()
              and table_name = 'post_comment'
              and column_name = ?
            """,
            Integer.class,
            columnName
        );
        if (columnCount != null && columnCount > 0) {
            return;
        }

        jdbcTemplate.execute(alterSql);
        log.info("Added missing post_comment.{} column automatically.", columnName);
    }

    private void ensurePostCommentLikeTable() {
        Integer tableCount = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.tables
            where table_schema = database()
              and table_name = 'post_comment_like'
            """,
            Integer.class
        );
        if (tableCount != null && tableCount > 0) {
            return;
        }

        jdbcTemplate.execute(
            """
            create table post_comment_like (
                id bigint primary key auto_increment,
                comment_id bigint not null,
                user_id bigint not null,
                created_at datetime not null default current_timestamp,
                unique key uk_post_comment_like (comment_id, user_id)
            ) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci
            """
        );
        log.info("Created missing post_comment_like table automatically.");
    }

    private void syncDefaultActivities() {
        if (!tableExists("activity_topic")) {
            return;
        }

        upsertNewStarActivity();
        migrateLegacyActivityRelations();
        upsertMerchantActivity();
        jdbcTemplate.update(
            "delete from activity_topic where activity_code in ('spring-library-week', 'club-style-challenge', 'budget-outfit-plan')"
        );
        log.info("Synced default activity topics to the V2 campaign set.");
    }

    private void migrateLegacyActivityRelations() {
        if (tableExists("user_activity_join")) {
            jdbcTemplate.update(
                """
                insert ignore into user_activity_join (activity_id, user_id, created_at)
                select 1, ua.user_id, ua.created_at
                from user_activity_join ua
                join activity_topic a on a.id = ua.activity_id
                where a.activity_code in ('club-style-challenge', 'budget-outfit-plan')
                """
            );
            jdbcTemplate.update(
                """
                delete ua
                from user_activity_join ua
                join activity_topic a on a.id = ua.activity_id
                where a.activity_code in ('club-style-challenge', 'budget-outfit-plan')
                """
            );
        }

        if (tableExists("post_activity_binding")) {
            jdbcTemplate.update(
                """
                update post_activity_binding pab
                join activity_topic a on a.id = pab.activity_id
                set pab.activity_id = 1,
                    pab.updated_at = now()
                where a.activity_code in ('club-style-challenge', 'budget-outfit-plan')
                """
            );
        }

        if (tableExists("post_draft")) {
            jdbcTemplate.update(
                """
                update post_draft
                set activity_code = ?,
                    updated_at = now()
                where activity_code in ('spring-library-week', 'club-style-challenge', 'budget-outfit-plan')
                """,
                NEW_STAR_ACTIVITY_CODE
            );
        }
    }

    private void upsertNewStarActivity() {
        jdbcTemplate.update(
            """
            insert into activity_topic (
                id, activity_code, title, badge_label, theme_desc, summary_desc, period_text,
                reward_desc, participation_desc, scene_label, status_code, featured_flag, heat_value,
                sort_order, status, start_time, end_time, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            on duplicate key update
                activity_code = values(activity_code),
                title = values(title),
                badge_label = values(badge_label),
                theme_desc = values(theme_desc),
                summary_desc = values(summary_desc),
                period_text = values(period_text),
                reward_desc = values(reward_desc),
                participation_desc = values(participation_desc),
                scene_label = values(scene_label),
                status_code = values(status_code),
                featured_flag = values(featured_flag),
                heat_value = values(heat_value),
                sort_order = values(sort_order),
                status = values(status),
                start_time = values(start_time),
                end_time = values(end_time),
                updated_at = now()
            """,
            1L,
            NEW_STAR_ACTIVITY_CODE,
            "校园穿搭新星计划",
            "内容冷启动",
            "面向首批创作者的内容冷启动活动，鼓励在上线前 7 天持续发布带校园场景标签的原创穿搭，用真实互动分找出最值得被看见的校园内容。",
            "发布带校园场景标签的原创穿搭即可参评，每周按互动分评选 Top 30 优质穿搭。",
            "2026.03.22 - 2026.03.28",
            "入选 Top 30 可参与瓜分 500 元启动奖金池，前三名额外获得首页活动推荐位和官方公告露出。",
            "发布原创穿搭并至少选择 1 个校园场景标签，内容通过审核后按点赞×1 + 评论×3 + 收藏×2 计入互动分。",
            "校园穿搭",
            "ONGOING",
            1,
            1280,
            1,
            1,
            Timestamp.valueOf(LocalDateTime.of(2026, 3, 22, 0, 0, 0)),
            Timestamp.valueOf(LocalDateTime.of(2026, 3, 28, 23, 59, 59))
        );
    }

    private void upsertMerchantActivity() {
        jdbcTemplate.update(
            """
            insert into activity_topic (
                id, activity_code, title, badge_label, theme_desc, summary_desc, period_text,
                reward_desc, participation_desc, scene_label, status_code, featured_flag, heat_value,
                sort_order, status, start_time, end_time, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            on duplicate key update
                activity_code = values(activity_code),
                title = values(title),
                badge_label = values(badge_label),
                theme_desc = values(theme_desc),
                summary_desc = values(summary_desc),
                period_text = values(period_text),
                reward_desc = values(reward_desc),
                participation_desc = values(participation_desc),
                scene_label = values(scene_label),
                status_code = values(status_code),
                featured_flag = values(featured_flag),
                heat_value = values(heat_value),
                sort_order = values(sort_order),
                status = values(status),
                start_time = values(start_time),
                end_time = values(end_time),
                updated_at = now()
            """,
            2L,
            MERCHANT_ACTIVITY_CODE,
            "首批商家扶持计划",
            "商家扶持",
            "面向首批校园服饰合作商家的冷启动扶持计划，优先通过合作内容、商品跳转统计、首页推荐位和轮播位帮助合作商品起量。",
            "首月完成合作登记的商家可进入扶持池；带真实商品链接并通过审核的合作穿搭，会进入额外曝光评估。",
            "2026.03.22 - 2026.04.21",
            "首月入驻免服务费；符合排期的合作商品可获首页活动推荐或 1 天轮播扶持，每周点击热度最高的合作商品可获平台免费帮推全校。",
            "由平台运营登记首批合作商家；创作者发布带真实商品链接并通过审核的合作穿搭即可进入扶持池，商品点击与内容互动会作为评估依据。",
            "商家合作",
            "RECRUITING",
            1,
            930,
            2,
            1,
            Timestamp.valueOf(LocalDateTime.of(2026, 3, 22, 0, 0, 0)),
            Timestamp.valueOf(LocalDateTime.of(2026, 4, 21, 23, 59, 59))
        );
    }

    private void syncDefaultAnnouncement() {
        if (!tableExists("official_announcement")) {
            return;
        }

        jdbcTemplate.update(
            """
            insert into official_announcement (
                id, title, badge_label, summary, content, status, pinned_flag, sort_order,
                publish_time, expire_time, created_by, updated_by, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            on duplicate key update
                title = values(title),
                badge_label = values(badge_label),
                summary = values(summary),
                content = values(content),
                status = values(status),
                pinned_flag = values(pinned_flag),
                sort_order = values(sort_order),
                publish_time = values(publish_time),
                expire_time = values(expire_time),
                updated_by = values(updated_by),
                updated_at = now()
            """,
            1L,
            "CampusFit V2 活动发布：新星计划与商家扶持上线",
            "活动发布",
            "活动中心已更新为 2 个新专题：面向创作者的“校园穿搭新星计划”，以及面向首批合作商家的“首批商家扶持计划”。",
            "活动中心现已下线旧的 3 个默认活动，并同步上线 2 个更贴合当前平台能力的新活动。校园穿搭新星计划面向首批创作者开放：发布带校园场景标签的原创穿搭并通过审核后，按点赞×1 + 评论×3 + 收藏×2 统计互动分，每周评选 Top 30，入选可参与 500 元启动奖金池，前三名额外获得首页推荐位。首批商家扶持计划面向首批合作商家与合作内容开放：首月入驻免服务费，创作者发布带真实商品链接并通过审核的合作穿搭即可进入扶持池，平台会结合商品点击热度、内容互动和合作排期给予首页推荐或轮播扶持。当前平台已支持活动报名、活动绑定发布、内容审核、首页推荐、商品链接与点击统计、官方公告发布，活动结果以现阶段运营评选和后台配置为准。",
            1,
            1,
            0,
            Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)),
            null,
            "系统运营",
            "系统运营"
        );
        log.info("Synced default homepage announcement for the V2 campaign release.");
    }

    private boolean tableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.tables
            where table_schema = database()
              and table_name = ?
            """,
            Integer.class,
            tableName
        );
        return tableCount != null && tableCount > 0;
    }
}
