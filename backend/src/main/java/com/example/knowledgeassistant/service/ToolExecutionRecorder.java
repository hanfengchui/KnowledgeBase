package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.ToolCallDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ToolExecutionRecorder {

    private static final Logger log = LoggerFactory.getLogger(ToolExecutionRecorder.class);

    private final ThreadLocal<List<ToolCallDto>> calls = new ThreadLocal<>();

    public void start() {
        calls.set(new ArrayList<>());
        log.debug("Tool execution recorder started");
    }

    public void record(String name, String arguments, String result) {
        List<ToolCallDto> currentCalls = calls.get();
        if (currentCalls != null) {
            currentCalls.add(new ToolCallDto(name, arguments, result));
        }
        log.info(
                "Tool call recorded name={} argumentChars={} resultChars={} activeRun={}",
                name,
                arguments == null ? 0 : arguments.length(),
                result == null ? 0 : result.length(),
                currentCalls != null
        );
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
