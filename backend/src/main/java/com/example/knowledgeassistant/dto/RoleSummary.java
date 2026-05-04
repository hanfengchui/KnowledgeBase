package com.example.knowledgeassistant.dto;

import java.util.List;
import java.util.UUID;

public record RoleSummary(
        UUID id,
        String code,
        String name,
        String scopeType,
        String description,
        List<String> permissionCodes
) {
}
