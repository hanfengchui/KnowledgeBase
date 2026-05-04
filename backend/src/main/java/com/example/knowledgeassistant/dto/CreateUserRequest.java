package com.example.knowledgeassistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateUserRequest(
        UUID tenantId,

        @NotBlank(message = "username must not be blank")
        @Size(max = 80, message = "username must be at most 80 characters")
        String username,

        @NotBlank(message = "displayName must not be blank")
        @Size(max = 120, message = "displayName must be at most 120 characters")
        String displayName,

        @Email(message = "email must be valid")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @NotBlank(message = "password must not be blank")
        @Size(min = 8, max = 120, message = "password must be between 8 and 120 characters")
        String password,

        List<String> tenantRoleCodes
) {
}
