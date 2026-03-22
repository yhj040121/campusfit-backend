package com.campusfit.modules.announcement.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.announcement.service.AnnouncementService;
import com.campusfit.modules.announcement.vo.AnnouncementVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbcTemplate;

    public AnnouncementServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AnnouncementVO getLatestPublished() {
        List<AnnouncementVO> items = jdbcTemplate.query(
            publishedAnnouncementSelect() + " order by pinned_flag desc, sort_order asc, publish_time desc, id desc limit 1",
            (rs, rowNum) -> mapAnnouncement(rs)
        );
        return items.isEmpty() ? null : items.get(0);
    }

    @Override
    public AnnouncementVO getPublishedDetail(Long announcementId) {
        List<AnnouncementVO> items = jdbcTemplate.query(
            publishedAnnouncementSelect() + " and id = ? limit 1",
            (rs, rowNum) -> mapAnnouncement(rs),
            announcementId
        );
        if (items.isEmpty()) {
            throw new BusinessException("公告不存在或已下线");
        }
        return items.get(0);
    }

    private String publishedAnnouncementSelect() {
        return """
            select
                id,
                badge_label,
                title,
                summary,
                content,
                publish_time,
                expire_time
            from official_announcement
            where status = 1
              and publish_time <= now()
              and (expire_time is null or expire_time >= now())
            """;
    }

    private AnnouncementVO mapAnnouncement(ResultSet rs) throws SQLException {
        return new AnnouncementVO(
            rs.getLong("id"),
            coalesce(rs.getString("badge_label"), "官方公告"),
            rs.getString("title"),
            rs.getString("summary"),
            coalesce(rs.getString("content"), rs.getString("summary")),
            formatDateTime(rs.getTimestamp("publish_time")),
            formatDateTime(rs.getTimestamp("expire_time"))
        );
    }

    private String formatDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.toLocalDateTime().format(DATE_TIME_FORMATTER);
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
