package com.example.aiinterview.dto;

import java.util.List;

public record AskResponse(
        String answer,
        List<SourceDto> sources,
        ChatStats stats,
        List<ToolCallDto> toolCalls
) {
}
