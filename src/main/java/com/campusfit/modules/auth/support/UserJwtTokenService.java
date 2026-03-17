package com.campusfit.modules.auth.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UserJwtTokenService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secretBytes;
    private final long expireSeconds;

    public UserJwtTokenService(
        ObjectMapper objectMapper,
        @Value("${app.jwt.user-secret:CampusFitUserSecretChangeMe1234567890}") String secret,
        @Value("${app.jwt.user-expire-hours:72}") long expireHours
    ) {
        this.objectMapper = objectMapper;
        this.secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        this.expireSeconds = Math.max(1L, expireHours) * 3600L;
    }

    public String createToken(Long userId, String phone, String nickname) {
        try {
            long issuedAt = Instant.now().getEpochSecond();
            long expiresAt = issuedAt + expireSeconds;

            Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT"
            );
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", String.valueOf(userId));
            payload.put("uid", userId);
            payload.put("phone", phone);
            payload.put("nickname", nickname);
            payload.put("iat", issuedAt);
            payload.put("exp", expiresAt);

            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String signingInput = headerPart + "." + payloadPart;
            String signaturePart = sign(signingInput);
            return signingInput + "." + signaturePart;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate JWT token", exception);
        }
    }

    public UserSession parseToken(String token) {
        ParsedToken parsedToken = verifyToken(token);
        if (parsedToken == null || parsedToken.expiresAt() <= Instant.now().getEpochSecond()) {
            return null;
        }
        return new UserSession(token, parsedToken.userId(), parsedToken.phone(), parsedToken.nickname());
    }

    public Long extractExpirationEpochSeconds(String token) {
        ParsedToken parsedToken = verifyToken(token);
        return parsedToken == null ? null : parsedToken.expiresAt();
    }

    private ParsedToken verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            String signingInput = parts[0] + "." + parts[1];
            byte[] expectedSignature = URL_DECODER.decode(sign(signingInput));
            byte[] actualSignature = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return null;
            }

            JsonNode payloadNode = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            JsonNode userIdNode = payloadNode.get("uid");
            JsonNode phoneNode = payloadNode.get("phone");
            JsonNode nicknameNode = payloadNode.get("nickname");
            JsonNode expiresAtNode = payloadNode.get("exp");
            if (userIdNode == null || expiresAtNode == null) {
                return null;
            }
            return new ParsedToken(
                userIdNode.asLong(),
                phoneNode == null ? null : phoneNode.asText(),
                nicknameNode == null ? null : nicknameNode.asText(),
                expiresAtNode.asLong()
            );
        } catch (Exception exception) {
            return null;
        }
    }

    private String encodeJson(Map<String, Object> content) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(content));
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(content.getBytes(StandardCharsets.UTF_8)));
    }

    private record ParsedToken(Long userId, String phone, String nickname, long expiresAt) {
    }
}
