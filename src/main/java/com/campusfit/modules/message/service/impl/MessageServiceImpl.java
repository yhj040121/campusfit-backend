package com.campusfit.modules.message.service.impl;

import com.campusfit.common.exception.BusinessException;
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
            select id, message_type, title, content, read_status, created_at
            from message_notification
            where user_id = ?
            order by created_at desc, id desc
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new MessageItemVO(
            String.valueOf(rs.getLong("id")),
            rs.getString("message_type"),
            rs.getString("title"),
            rs.getString("content"),
            formatRelativeTime(rs.getTimestamp("created_at")),
            rs.getInt("read_status") == 1
        ), currentUserId);
    }

    @Override
    public int countUnread() {
        long currentUserId = UserAuthContext.requireUserId();
        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from message_notification where user_id = ? and read_status = 0",
            Integer.class,
            currentUserId
        );
        return count == null ? 0 : count;
    }

    @Override
    public boolean markRead(String messageId) {
        long currentUserId = UserAuthContext.requireUserId();
        long id = parseMessageId(messageId);
        int updated = jdbcTemplate.update(
            "update message_notification set read_status = 1 where id = ? and user_id = ? and read_status = 0",
            id,
            currentUserId
        );
        return updated > 0;
    }

    @Override
    public int markAllRead() {
        long currentUserId = UserAuthContext.requireUserId();
        return jdbcTemplate.update(
            "update message_notification set read_status = 1 where user_id = ? and read_status = 0",
            currentUserId
        );
    }

    @Override
    public boolean deleteMessage(String messageId) {
        long currentUserId = UserAuthContext.requireUserId();
        long id = parseMessageId(messageId);
        int deleted = jdbcTemplate.update(
            "delete from message_notification where id = ? and user_id = ?",
            id,
            currentUserId
        );
        return deleted > 0;
    }

    @Override
    public int deleteReadMessages() {
        long currentUserId = UserAuthContext.requireUserId();
        return jdbcTemplate.update(
            "delete from message_notification where user_id = ? and read_status = 1",
            currentUserId
        );
    }

    private long parseMessageId(String messageId) {
        try {
            return Long.parseLong(messageId);
        } catch (NumberFormatException exception) {
            throw new BusinessException("\u6d88\u606f\u7f16\u53f7\u65e0\u6548");
        }
    }

    private String formatRelativeTime(Timestamp createdAt) {
        if (createdAt == null) {
            return "\u521a\u521a";
        }
        Duration duration = Duration.between(createdAt.toLocalDateTime(), LocalDateTime.now());
        if (duration.isNegative() || duration.toMinutes() <= 0) {
            return "\u521a\u521a";
        }
        if (duration.toMinutes() < 60) {
            return duration.toMinutes() + " \u5206\u949f\u524d";
        }
        if (duration.toHours() < 24) {
            return duration.toHours() + " \u5c0f\u65f6\u524d";
        }
        if (duration.toDays() < 7) {
            return duration.toDays() + " \u5929\u524d";
        }
        return createdAt.toLocalDateTime().toLocalDate().toString();
    }
}
