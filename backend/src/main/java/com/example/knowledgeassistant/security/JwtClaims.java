package com.example.knowledgeassistant.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JwtClaims(
        String subject,
        UUID userId,
        String username,
        UUID tenantId,
        String tenantCode,
        List<String> roleCodes,
        String tokenId,
        Instant issuedAt,
        Instant expiresAt
) {
}
