package com.example.aiinterview.dto;

import java.util.UUID;

public record UploadResponse(
        UUID documentId,
        String fileName,
        int chunkCount,
        String message
) {
}
