package com.campusfit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(JdbcTemplate.class)
public class DatabaseSchemaRepairRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaRepairRunner.class);

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
}
