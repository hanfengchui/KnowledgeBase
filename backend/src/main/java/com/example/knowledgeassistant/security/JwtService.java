package com.example.knowledgeassistant.security;

import com.example.knowledgeassistant.config.SecurityProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(SecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public IssuedToken issueToken(
            UUID userId,
            String username,
            UUID tenantId,
            String tenantCode,
            Set<String> roleCodes
    ) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(properties.getAccessTokenTtlMinutes() * 60);
            String tokenId = UUID.randomUUID().toString();

            Map<String, Object> header = Map.of(
                    "alg", "HS256",
                    "typ", "JWT"
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", username);
            payload.put("userId", userId.toString());
            payload.put("username", username);
            payload.put("tenantId", tenantId == null ? null : tenantId.toString());
            payload.put("tenantCode", tenantCode);
            payload.put("roleCodes", roleCodes == null ? List.of() : roleCodes.stream().sorted().toList());
            payload.put("jti", tokenId);
            payload.put("iat", now.getEpochSecond());
            payload.put("exp", expiresAt.getEpochSecond());

            String headerPart = encodeJson(header);
            String payloadPart = encodeJson(payload);
            String signature = sign(headerPart + "." + payloadPart);
            return new IssuedToken(headerPart + "." + payloadPart + "." + signature, tokenId, expiresAt);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to issue JWT", ex);
        }
    }

    public JwtClaims parse(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new InvalidTokenException("Token format is invalid");
            }

            String signingInput = parts[0] + "." + parts[1];
            String expectedSignature = sign(signingInput);
            if (!MessageDigest.isEqual(expectedSignature.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new InvalidTokenException("Token signature is invalid");
            }

            Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
            Instant expiresAt = Instant.ofEpochSecond(longValue(payload.get("exp")));
            if (expiresAt.isBefore(Instant.now())) {
                throw new InvalidTokenException("Token has expired");
            }

            Instant issuedAt = Instant.ofEpochSecond(longValue(payload.get("iat")));
            List<String> roleCodes = objectMapper.convertValue(payload.getOrDefault("roleCodes", List.of()), new TypeReference<List<String>>() {
            });

            return new JwtClaims(
                    stringValue(payload.get("sub")),
                    uuidValue(payload.get("userId")),
                    stringValue(payload.get("username")),
                    uuidValue(payload.get("tenantId")),
                    stringValue(payload.get("tenantCode")),
                    roleCodes,
                    stringValue(payload.get("jti")),
                    issuedAt,
                    expiresAt
            );
        } catch (InvalidTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidTokenException("Token parsing failed", ex);
        }
    }

    private String encodeJson(Object value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private UUID uuidValue(Object value) {
        String raw = stringValue(value);
        return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public record IssuedToken(String token, String tokenId, Instant expiresAt) {
    }
}
