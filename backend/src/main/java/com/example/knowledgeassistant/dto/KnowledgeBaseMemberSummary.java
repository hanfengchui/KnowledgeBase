package com.example.knowledgeassistant.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KnowledgeBaseMemberSummary(
        UUID knowledgeBaseId,
        UUID userId,
        String username,
        String displayName,
        String email,
        List<String> roleCodes,
        Instant grantedAt
) {
}
