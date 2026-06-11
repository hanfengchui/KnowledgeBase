package com.example.knowledgeassistant.dto;

import java.util.List;
import java.util.UUID;

public record AgentTraceDto(
        UUID runId,
        String status,
        List<AgentStepDto> steps
) {
}
