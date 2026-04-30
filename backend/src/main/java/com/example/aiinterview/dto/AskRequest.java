package com.example.aiinterview.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AskRequest(
        @NotBlank(message = "question must not be blank")
        String question,

        @Min(value = 1, message = "topK must be at least 1")
        @Max(value = 10, message = "topK must be at most 10")
        Integer topK
) {
}
