package com.example.knowledgeassistant.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record KnowledgeBaseMemberRequest(
        @NotNull(message = "userId must not be null")
        UUID userId,
        List<String> roleCodes
) {
}
