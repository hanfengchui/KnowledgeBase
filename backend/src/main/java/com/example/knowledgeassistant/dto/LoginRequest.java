package com.example.knowledgeassistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 80, message = "username must be at most 80 characters")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(max = 120, message = "password must be at most 120 characters")
        String password,

        @Size(max = 80, message = "tenantCode must be at most 80 characters")
        String tenantCode
) {
}
