package com.example.aiinterview.dto;

public record SourceDto(
        String documentId,
        String documentName,
        Integer chunkIndex,
        Double score,
        String content
) {
}
