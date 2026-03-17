package com.campusfit.modules.auth.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.auth.dto.UserLoginRequest;
import com.campusfit.modules.auth.dto.UserRegisterRequest;
import com.campusfit.modules.auth.service.UserAuthService;
import com.campusfit.modules.auth.support.UserJwtTokenService;
import com.campusfit.modules.auth.support.UserSession;
import com.campusfit.modules.auth.vo.UserAuthResultVO;
import com.campusfit.modules.auth.vo.UserSessionVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    private static final String DEMO_CODE = "2468";

    private final JdbcTemplate jdbcTemplate;
    private final UserJwtTokenService userJwtTokenService;
    private final Map<String, Long> revokedTokenStore = new ConcurrentHashMap<>();

    public UserAuthServiceImpl(JdbcTemplate jdbcTemplate, UserJwtTokenService userJwtTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userJwtTokenService = userJwtTokenService;
    }

    @Override
    public UserAuthResultVO login(UserLoginRequest request) {
        validateCode(request.code());
        UserRecord user = requireUserByPhone(request.phone());
        return buildTokenResult(user);
    }

    @Override
    @Transactional
    public UserAuthResultVO register(UserRegisterRequest request) {
        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from app_user where phone = ?",
            Integer.class,
            request.phone()
        );
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已注册，请直接登录。");
        }

        GeneratedIdHolder holder = new GeneratedIdHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                "insert into app_user (phone, nickname, avatar_url, status, created_at, updated_at) values (?, ?, null, 1, now(), now())",
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, request.phone());
            statement.setString(2, request.nickname().trim());
            return statement;
        }, holder);

        Long userId = holder.id();
        if (userId == null) {
            throw new BusinessException("创建用户账号失败");
        }

        String schoolName = normalize(request.schoolName());
        String gradeName = normalize(request.gradeName());
        String signature = normalize(request.signature());
        String nickname = request.nickname().trim();
        String avatarText = nickname.isEmpty() ? "C" : nickname.substring(0, 1);

        jdbcTemplate.update(
            "insert into user_profile (user_id, school_name, grade_name, signature, avatar_text, avatar_class, like_count, follower_count, following_count, created_at, updated_at) values (?, ?, ?, ?, ?, ?, 0, 0, 0, now(), now())",
            userId,
            schoolName,
            gradeName,
            signature,
            avatarText,
            "soft"
        );

        jdbcTemplate.update(
            "insert into message_notification (user_id, message_type, title, content, read_status, created_at) values (?, ?, ?, ?, 0, now())",
            userId,
            "系统通知",
            "欢迎来到 CampusFit",
            "你的账号已创建成功，快去探索校园穿搭和创作者推荐吧。"
        );

        return buildTokenResult(new UserRecord(userId, request.phone(), nickname, 1));
    }

    @Override
    public UserSessionVO currentUser(UserSession session) {
        UserSession safeSession = requireSession(session);
        UserRecord currentUser = requireUserById(safeSession.userId());
        return new UserSessionVO(currentUser.id(), currentUser.phone(), currentUser.nickname());
    }

    @Override
    public UserSession findByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        clearExpiredRevokedTokens();
        Long revokedUntil = revokedTokenStore.get(token);
        if (revokedUntil != null && revokedUntil > Instant.now().getEpochSecond()) {
            return null;
        }

        UserSession parsed = userJwtTokenService.parseToken(token);
        if (parsed == null) {
            return null;
        }

        try {
            UserRecord user = requireUserById(parsed.userId());
            return new UserSession(token, user.id(), user.phone(), user.nickname());
        } catch (BusinessException exception) {
            return null;
        }
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        Long expiresAt = userJwtTokenService.extractExpirationEpochSeconds(token);
        if (expiresAt != null) {
            revokedTokenStore.put(token, expiresAt);
        }
        clearExpiredRevokedTokens();
    }

    @Override
    public void refreshNickname(Long userId, String nickname) {
        // JWT is stateless. Fresh nickname data is loaded from the database on subsequent requests.
    }

    private void validateCode(String code) {
        if (!DEMO_CODE.equals(code)) {
            throw new BusinessException("验证码不正确，演示环境请使用 2468。");
        }
    }

    private UserSession requireSession(UserSession session) {
        if (session == null) {
            throw new BusinessException("请先登录");
        }
        return session;
    }

    private UserAuthResultVO buildTokenResult(UserRecord user) {
        String token = userJwtTokenService.createToken(user.id(), user.phone(), user.nickname());
        return new UserAuthResultVO(token, user.id(), user.phone(), user.nickname());
    }

    private UserRecord requireUserByPhone(String phone) {
        List<UserRecord> users = jdbcTemplate.query(
            "select id, phone, nickname, status from app_user where phone = ? limit 1",
            (rs, rowNum) -> new UserRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getInt("status")
            ),
            phone
        );
        if (users.isEmpty()) {
            throw new BusinessException("该手机号未注册，请先完成注册。");
        }
        UserRecord user = users.get(0);
        if (user.status() != 1) {
            throw new BusinessException("该账号当前不可用，请联系平台管理员。");
        }
        return user;
    }

    private UserRecord requireUserById(Long userId) {
        List<UserRecord> users = jdbcTemplate.query(
            "select id, phone, nickname, status from app_user where id = ? limit 1",
            (rs, rowNum) -> new UserRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getInt("status")
            ),
            userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("未找到当前用户账号");
        }
        UserRecord user = users.get(0);
        if (user.status() != 1) {
            throw new BusinessException("该账号当前不可用，请联系平台管理员。");
        }
        return user;
    }

    private void clearExpiredRevokedTokens() {
        long now = Instant.now().getEpochSecond();
        revokedTokenStore.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= now);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record UserRecord(Long id, String phone, String nickname, int status) {
    }

    private static final class GeneratedIdHolder extends org.springframework.jdbc.support.GeneratedKeyHolder {
        Long id() {
            Number key = getKey();
            return key == null ? null : key.longValue();
        }
    }
}
