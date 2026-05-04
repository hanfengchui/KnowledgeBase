package com.example.knowledgeassistant.dto;

import java.util.UUID;

public record UploadResponse(
        UUID documentId,
        UUID knowledgeBaseId,
        String knowledgeBaseName,
        String fileName,
        String documentType,
        String status,
        int chunkCount,
        String message
) {
}
