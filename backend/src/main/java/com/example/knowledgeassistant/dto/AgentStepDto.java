package com.example.knowledgeassistant.dto;

public record AgentStepDto(
        int stepIndex,
        String stepType,
        String name,
        String status,
        String inputSummary,
        String outputSummary,
        long latencyMs
) {
}
