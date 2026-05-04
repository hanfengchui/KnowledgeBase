package com.example.knowledgeassistant.dto;

import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @Size(max = 32, message = "status must be at most 32 characters")
        String status
) {
}
