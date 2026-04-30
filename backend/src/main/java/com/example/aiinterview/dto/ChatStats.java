package com.example.aiinterview.dto;

public record ChatStats(
        long latencyMs,
        int retrievedCount,
        String chatModel,
        String embeddingModel,
        boolean usedRag,
        boolean usedTools
) {
}
