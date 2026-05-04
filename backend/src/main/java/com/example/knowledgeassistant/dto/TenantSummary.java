package com.example.knowledgeassistant.dto;

import java.time.Instant;
import java.util.UUID;

public record TenantSummary(
        UUID id,
        String code,
        String name,
        String status,
        Instant createdAt,
        long userCount,
        long knowledgeBaseCount
) {
}
