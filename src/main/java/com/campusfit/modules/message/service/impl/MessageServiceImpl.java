package com.campusfit.modules.message.service.impl;

import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.message.service.MessageService;
import com.campusfit.modules.message.vo.MessageItemVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageServiceImpl implements MessageService {

    private final JdbcTemplate jdbcTemplate;

    public MessageServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<MessageItemVO> listMessages() {
        long currentUserId = UserAuthContext.requireUserId();
        String sql = """
            select id, message_type, title, content, created_at
            from message_notification
            where user_id = ?
            order by created_at desc, id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MessageItemVO(
            String.valueOf(rs.getLong("id")),
            rs.getString("message_type"),
            rs.getString("title"),
            rs.getString("content"),
            formatRelativeTime(rs.getTimestamp("created_at"))
        ), currentUserId);
    }

    private String formatRelativeTime(Timestamp createdAt) {
        if (createdAt == null) {
            return "Just now";
        }
        Duration duration = Duration.between(createdAt.toLocalDateTime(), LocalDateTime.now());
        if (duration.isNegative() || duration.toMinutes() <= 0) {
            return "Just now";
        }
        if (duration.toMinutes() < 60) {
            return duration.toMinutes() + " min ago";
        }
        if (duration.toHours() < 24) {
            return duration.toHours() + " h ago";
        }
        if (duration.toDays() < 7) {
            return duration.toDays() + " d ago";
        }
        return createdAt.toLocalDateTime().toLocalDate().toString();
    }
}