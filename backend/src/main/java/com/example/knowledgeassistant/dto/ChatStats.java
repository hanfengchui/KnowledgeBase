package com.example.knowledgeassistant.dto;

import java.util.UUID;

public record ChatStats(
        long latencyMs,
        int retrievedCount,
        String chatModel,
        String embeddingModel,
        boolean usedRag,
        boolean usedTools,
        UUID knowledgeBaseId,
        String knowledgeBaseName
) {
}
