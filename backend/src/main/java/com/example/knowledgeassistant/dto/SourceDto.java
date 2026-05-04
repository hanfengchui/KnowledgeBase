package com.example.knowledgeassistant.dto;

public record SourceDto(
        String tenantId,
        String knowledgeBaseId,
        String knowledgeBaseName,
        String documentId,
        String documentName,
        String documentType,
        Integer chunkIndex,
        Double score,
        String content
) {
}
