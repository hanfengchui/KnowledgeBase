package com.example.knowledgeassistant.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record KnowledgeBaseAccessDto(
        UUID knowledgeBaseId,
        String knowledgeBaseName,
        List<String> roleCodes,
        Set<String> permissionCodes
) {
}
