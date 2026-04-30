package com.example.aiinterview.dto;

public record ReindexResponse(
        int chunkCount,
        String vectorTableName,
        String message
) {
}
