package com.campusfit.common.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileSchemaPatchRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public ProfileSchemaPatchRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureUserProfileColumn("cover_image_url", "varchar(255) null after avatar_class");
        ensureUserProfileColumn("gender", "varchar(20) null after cover_image_url");
        ensureUserProfileColumn("email", "varchar(120) null after gender");
        ensureUserProfileColumn("location_name", "varchar(100) null after email");
    }

    private void ensureUserProfileColumn(String columnName, String ddl) {
        List<String> columns = jdbcTemplate.query(
            "show columns from user_profile like ?",
            (rs, rowNum) -> rs.getString("Field")
            ,
            columnName
        );
        if (!columns.isEmpty()) {
            return;
        }
        jdbcTemplate.execute("alter table user_profile add column " + columnName + " " + ddl);
    }
}
