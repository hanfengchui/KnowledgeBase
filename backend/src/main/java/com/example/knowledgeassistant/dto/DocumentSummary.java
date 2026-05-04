package com.example.knowledgeassistant.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        UUID knowledgeBaseId,
        String knowledgeBaseName,
        String fileName,
        String documentType,
        String status,
        int chunkCount,
        int charCount,
        Instant createdAt,
        Instant updatedAt,
        String errorMessage,
        String contentHash
) {
}
