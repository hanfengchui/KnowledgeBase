package com.example.aiinterview.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
        UUID id,
        String fileName,
        int chunkCount,
        Instant createdAt,
        String contentHash
) {
}
