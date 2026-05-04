package com.example.knowledgeassistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(max = 120, message = "displayName must be at most 120 characters")
        String displayName,

        @Email(message = "email must be valid")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        @Size(max = 32, message = "status must be at most 32 characters")
        String status
) {
}
