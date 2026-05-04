package com.example.knowledgeassistant.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserSummary(
        UUID id,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        String username,
        String displayName,
        String email,
        String status,
        Instant createdAt,
        List<String> platformRoleCodes,
        List<String> tenantRoleCodes
) {
}
