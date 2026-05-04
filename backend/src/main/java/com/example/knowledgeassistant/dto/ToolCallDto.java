package com.example.knowledgeassistant.dto;

public record ToolCallDto(
        String name,
        String arguments,
        String result
) {
}
