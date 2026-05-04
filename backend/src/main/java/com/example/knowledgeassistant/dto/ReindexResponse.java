package com.example.knowledgeassistant.dto;

import java.util.UUID;

public record ReindexResponse(
        int chunkCount,
        UUID knowledgeBaseId,
        String knowledgeBaseName,
        String vectorTableName,
        String message
) {
}
