package com.example.knowledgeassistant.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class AgentToolRegistry {

    private final OrderTools orderTools;
    private final KnowledgeBaseTools knowledgeBaseTools;

    public AgentToolRegistry(OrderTools orderTools, KnowledgeBaseTools knowledgeBaseTools) {
        this.orderTools = orderTools;
        this.knowledgeBaseTools = knowledgeBaseTools;
    }

    public List<ToolCallback> knowledgeTools() {
        return toolCallbacks(knowledgeBaseTools);
    }

    public List<ToolCallback> businessTools() {
        return toolCallbacks(orderTools);
    }

    public List<ToolCallback> allowedTools(boolean canUseBusinessTools) {
        if (canUseBusinessTools) {
            return toolCallbacks(knowledgeBaseTools, orderTools);
        }
        return knowledgeTools();
    }

    private List<ToolCallback> toolCallbacks(Object... toolObjects) {
        return Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects)
                .build()
                .getToolCallbacks());
    }
}
