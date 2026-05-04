package com.example.knowledgeassistant.dto;

import java.time.Instant;
import java.util.UUID;

public record KnowledgeBaseSummary(
        UUID id,
        UUID tenantId,
        String code,
        String name,
        String description,
        boolean isDefault,
        Instant createdAt,
        long documentCount
) {
}
