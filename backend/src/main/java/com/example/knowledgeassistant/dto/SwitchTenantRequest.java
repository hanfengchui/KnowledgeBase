package com.example.knowledgeassistant.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SwitchTenantRequest(
        @NotNull(message = "tenantId must not be null")
        UUID tenantId
) {
}
