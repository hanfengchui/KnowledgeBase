package com.example.knowledgeassistant.dto;

import java.util.UUID;

public record AvailableTenantDto(
        UUID id,
        String code,
        String name,
        String status
) {
}
