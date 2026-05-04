package com.example.knowledgeassistant.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogSummary(
        UUID id,
        UUID tenantId,
        String tenantName,
        UUID userId,
        String username,
        String action,
        String resourceType,
        String resourceId,
        String requestPath,
        String requestMethod,
        String requestPayloadSummary,
        int responseStatus,
        Instant createdAt
) {
}
