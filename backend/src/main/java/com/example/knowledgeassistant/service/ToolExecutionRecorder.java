package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.ToolCallDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolExecutionRecorder {

    private final ThreadLocal<List<ToolCallDto>> calls = new ThreadLocal<>();

    public void start() {
        calls.set(new ArrayList<>());
    }

    public void record(String name, String arguments, String result) {
        List<ToolCallDto> currentCalls = calls.get();
        if (currentCalls != null) {
            currentCalls.add(new ToolCallDto(name, arguments, result));
        }
    }

    public List<ToolCallDto> drain() {
        try {
            List<ToolCallDto> currentCalls = calls.get();
            return currentCalls == null ? List.of() : List.copyOf(currentCalls);
        } finally {
            calls.remove();
        }
    }
}
