package com.campusfit.modules.auth.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.auth.dto.UserLoginRequest;
import com.campusfit.modules.auth.dto.UserRegisterRequest;
import com.campusfit.modules.auth.dto.UserSendCodeRequest;
import com.campusfit.modules.auth.service.UserAuthService;
import com.campusfit.modules.auth.support.UserJwtTokenService;
import com.campusfit.modules.auth.support.UserSession;
import com.campusfit.modules.auth.vo.UserAuthResultVO;
import com.campusfit.modules.auth.vo.UserSendCodeVO;
import com.campusfit.modules.auth.vo.UserSessionVO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserAuthServiceImpl implements UserAuthService {

    private static final String DEMO_CODE = "040121";
    private static final String LOGIN_SCENE = "login";
    private static final String REGISTER_SCENE = "register";
    private static final String LOGIN_TYPE_PASSWORD = "password";
    private static final String LOGIN_TYPE_CODE = "code";
    private static final String LEGACY_DEFAULT_PASSWORD = "campus123";
    private static final String HASH_PREFIX = "pbkdf2_sha256";
    private static final int HASH_ITERATIONS = 65536;
    private static final int HASH_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final int CODE_EXPIRE_SECONDS = 300;
    private static final int SEND_CODE_INTERVAL_SECONDS = 60;

    private final JdbcTemplate jdbcTemplate;
    private final UserJwtTokenService userJwtTokenService;
    private final Map<String, Long> revokedTokenStore = new ConcurrentHashMap<>();
    private final Map<String, CodeTicket> codeStore = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAuthServiceImpl(JdbcTemplate jdbcTemplate, UserJwtTokenService userJwtTokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userJwtTokenService = userJwtTokenService;
    }

    @Override
    public UserSendCodeVO sendCode(UserSendCodeRequest request) {
        String phone = request.phone().trim();
        String scene = normalizeScene(request.scene());
        validateSendCodeScene(phone, scene);

        clearExpiredCodeTickets();
        long now = Instant.now().getEpochSecond();
        String codeKey = buildCodeKey(phone, scene);
        CodeTicket existingTicket = codeStore.get(codeKey);
        if (existingTicket != null && existingTicket.retryAvailableAt() > now) {
            long waitSeconds = existingTicket.retryAvailableAt() - now;
            throw new BusinessException("验证码发送过于频繁，请在 " + waitSeconds + " 秒后重试。");
        }

        codeStore.put(
            codeKey,
            new CodeTicket(DEMO_CODE, now + CODE_EXPIRE_SECONDS, now + SEND_CODE_INTERVAL_SECONDS)
        );
        return new UserSendCodeVO(phone, DEMO_CODE, CODE_EXPIRE_SECONDS, SEND_CODE_INTERVAL_SECONDS, true);
    }

    @Override
    public UserAuthResultVO login(UserLoginRequest request) {
        UserRecord user = requireUserByPhone(request.phone());
        String loginType = normalizeLoginType(request.loginType(), request.password(), request.code());
        if (LOGIN_TYPE_CODE.equals(loginType)) {
            validateCode(request.code());
            return buildTokenResult(user);
        }

        verifyPassword(user, request.password());
        if (needsPasswordUpgrade(user.passwordHash())) {
            upgradePasswordHash(user.id(), request.password());
            user = requireUserById(user.id());
        }
        return buildTokenResult(user);
    }

    @Override
    @Transactional
    public UserAuthResultVO register(UserRegisterRequest request) {
        validateCode(request.code());
        validatePasswordPair(request.password(), request.confirmPassword());

        Integer exists = jdbcTemplate.queryForObject(
            "select count(*) from app_user where phone = ?",
            Integer.class,
            request.phone()
        );
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已经注册，请直接登录。");
        }

        String nickname = request.nickname().trim();
        String avatarUrl = normalize(request.avatarUrl());
        String gender = normalizeGenderValue(request.gender());
        String email = normalize(request.email());
        String locationName = normalize(request.locationName());
        String passwordHash = hashPassword(request.password().trim());

        GeneratedIdHolder holder = new GeneratedIdHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                """
                insert into app_user (phone, nickname, avatar_url, password_hash, status, created_at, updated_at)
                values (?, ?, ?, ?, 1, now(), now())
                """,
                Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, request.phone());
            statement.setString(2, nickname);
            statement.setString(3, avatarUrl);
            statement.setString(4, passwordHash);
            return statement;
        }, holder);

        Long userId = holder.id();
        if (userId == null) {
            throw new BusinessException("创建用户账号失败");
        }

        jdbcTemplate.update(
            """
            insert into user_profile (
                user_id, school_name, grade_name, signature, gender, email, location_name, avatar_text, avatar_class,
                created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())
            """,
            userId,
            normalize(request.schoolName()),
            normalize(request.gradeName()),
            normalize(request.signature()),
            gender,
            email,
            locationName,
            nickname.substring(0, 1),
            "soft"
        );

        jdbcTemplate.update(
            """
            insert into message_notification (user_id, message_type, title, content, read_status, created_at)
            values (?, ?, ?, ?, 0, now())
            """,
            userId,
            "系统通知",
            "欢迎来到 青搭",
            "你的账号已经创建成功，去完善资料并开始发布第一条校园穿搭吧。"
        );

        return buildTokenResult(new UserRecord(userId, request.phone(), nickname, avatarUrl, passwordHash, 1));
    }

    @Override
    public UserSessionVO currentUser(UserSession session) {
        UserSession safeSession = requireSession(session);
        UserRecord currentUser = requireUserById(safeSession.userId());
        return new UserSessionVO(currentUser.id(), currentUser.phone(), currentUser.nickname(), currentUser.avatarUrl());
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
        // JWT is stateless. Fresh profile data is loaded from the database on subsequent requests.
    }

    private void validateCode(String code) {
        if (!DEMO_CODE.equals(normalize(code))) {
            throw new BusinessException("验证码不正确，当前统一使用 040121。");
        }
    }

    private void validateSendCodeScene(String phone, String scene) {
        if (LOGIN_SCENE.equals(scene)) {
            requireUserByPhone(phone);
            return;
        }
        if (REGISTER_SCENE.equals(scene)) {
            Integer exists = jdbcTemplate.queryForObject(
                "select count(*) from app_user where phone = ?",
                Integer.class,
                phone
            );
            if (exists != null && exists > 0) {
                throw new BusinessException("该手机号已经注册，请直接登录。");
            }
        }
    }

    private void validatePasswordPair(String password, String confirmPassword) {
        String normalizedPassword = normalize(password);
        String normalizedConfirmPassword = normalize(confirmPassword);
        if (normalizedPassword == null || normalizedPassword.length() < 6 || normalizedPassword.length() > 20) {
            throw new BusinessException("密码长度需要保持在 6 到 20 位之间。");
        }
        if (!normalizedPassword.equals(normalizedConfirmPassword)) {
            throw new BusinessException("两次输入的密码不一致。");
        }
    }

    private void verifyPassword(UserRecord user, String rawPassword) {
        String normalizedPassword = normalize(rawPassword);
        if (normalizedPassword == null) {
            throw new BusinessException("请输入密码。");
        }
        String storedPasswordHash = user.passwordHash();
        if (storedPasswordHash == null || storedPasswordHash.isBlank()) {
            if (!LEGACY_DEFAULT_PASSWORD.equals(normalizedPassword)) {
                throw new BusinessException("旧测试账号请使用初始密码 campus123 登录。");
            }
            return;
        }
        if (storedPasswordHash.startsWith(HASH_PREFIX + "$")) {
            if (!matchesPassword(normalizedPassword, storedPasswordHash)) {
                throw new BusinessException("手机号或密码不正确。");
            }
            return;
        }
        if (!storedPasswordHash.equals(normalizedPassword)) {
            throw new BusinessException("手机号或密码不正确。");
        }
    }

    private boolean needsPasswordUpgrade(String storedPasswordHash) {
        return storedPasswordHash == null
            || storedPasswordHash.isBlank()
            || !storedPasswordHash.startsWith(HASH_PREFIX + "$");
    }

    private void upgradePasswordHash(Long userId, String rawPassword) {
        jdbcTemplate.update(
            "update app_user set password_hash = ?, updated_at = now() where id = ?",
            hashPassword(rawPassword.trim()),
            userId
        );
    }

    private UserSession requireSession(UserSession session) {
        if (session == null) {
            throw new BusinessException("请先登录。");
        }
        return session;
    }

    private UserAuthResultVO buildTokenResult(UserRecord user) {
        String token = userJwtTokenService.createToken(user.id(), user.phone(), user.nickname());
        return new UserAuthResultVO(token, user.id(), user.phone(), user.nickname(), user.avatarUrl());
    }

    private UserRecord requireUserByPhone(String phone) {
        List<UserRecord> users = jdbcTemplate.query(
            "select id, phone, nickname, avatar_url, password_hash, status from app_user where phone = ? limit 1",
            (rs, rowNum) -> new UserRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getString("password_hash"),
                rs.getInt("status")
            ),
            phone
        );
        if (users.isEmpty()) {
            throw new BusinessException("该手机号尚未注册，请先完成注册。");
        }
        UserRecord user = users.get(0);
        if (user.status() != 1) {
            throw new BusinessException("该账号当前不可用，请联系平台管理员。");
        }
        return user;
    }

    private UserRecord requireUserById(Long userId) {
        List<UserRecord> users = jdbcTemplate.query(
            "select id, phone, nickname, avatar_url, password_hash, status from app_user where id = ? limit 1",
            (rs, rowNum) -> new UserRecord(
                rs.getLong("id"),
                rs.getString("phone"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getString("password_hash"),
                rs.getInt("status")
            ),
            userId
        );
        if (users.isEmpty()) {
            throw new BusinessException("未找到当前用户账号。");
        }
        UserRecord user = users.get(0);
        if (user.status() != 1) {
            throw new BusinessException("该账号当前不可用，请联系平台管理员。");
        }
        return user;
    }

    private String hashPassword(String rawPassword) {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(rawPassword.toCharArray(), salt, HASH_ITERATIONS, HASH_BITS);
        return HASH_PREFIX
            + "$" + HASH_ITERATIONS
            + "$" + Base64.getEncoder().encodeToString(salt)
            + "$" + Base64.getEncoder().encodeToString(hash);
    }

    private boolean matchesPassword(String rawPassword, String storedPasswordHash) {
        try {
            String[] parts = storedPasswordHash.split("\\$");
            if (parts.length != 4 || !HASH_PREFIX.equals(parts[0])) {
                return false;
            }
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[3]);
            byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt, iterations, expectedHash.length * 8);
            return java.security.MessageDigest.isEqual(expectedHash, actualHash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt, int iterations, int bits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, bits);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to hash password", exception);
        }
    }

    private void clearExpiredRevokedTokens() {
        long now = Instant.now().getEpochSecond();
        revokedTokenStore.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() <= now);
    }

    private void clearExpiredCodeTickets() {
        long now = Instant.now().getEpochSecond();
        codeStore.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAt() <= now);
    }

    private String normalizeScene(String scene) {
        String normalizedScene = normalize(scene);
        if (REGISTER_SCENE.equalsIgnoreCase(normalizedScene)) {
            return REGISTER_SCENE;
        }
        return LOGIN_SCENE.equalsIgnoreCase(normalizedScene) ? LOGIN_SCENE : "auth";
    }

    private String normalizeLoginType(String loginType, String password, String code) {
        String normalizedType = normalize(loginType);
        if (LOGIN_TYPE_CODE.equalsIgnoreCase(normalizedType)) {
            return LOGIN_TYPE_CODE;
        }
        if (LOGIN_TYPE_PASSWORD.equalsIgnoreCase(normalizedType)) {
            return LOGIN_TYPE_PASSWORD;
        }
        if (normalize(code) != null && normalize(password) == null) {
            return LOGIN_TYPE_CODE;
        }
        return LOGIN_TYPE_PASSWORD;
    }

    private String buildCodeKey(String phone, String scene) {
        return phone + "#" + scene;
    }

    private String normalizeGenderValue(String gender) {
        String normalizedGender = normalize(gender);
        if ("male".equalsIgnoreCase(normalizedGender)) {
            return "male";
        }
        if ("female".equalsIgnoreCase(normalizedGender)) {
            return "female";
        }
        return null;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record UserRecord(
        Long id,
        String phone,
        String nickname,
        String avatarUrl,
        String passwordHash,
        int status
    ) {
    }

    private static final class GeneratedIdHolder extends org.springframework.jdbc.support.GeneratedKeyHolder {
        Long id() {
            Number key = getKey();
            return key == null ? null : key.longValue();
        }
    }

    private record CodeTicket(
        String code,
        long expiresAt,
        long retryAvailableAt
    ) {
    }
}
