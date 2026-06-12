package com.example.knowledgeassistant.tool;

import com.example.knowledgeassistant.dto.SourceDto;
import com.example.knowledgeassistant.service.KnowledgeBaseService;
import com.example.knowledgeassistant.service.ToolExecutionRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class KnowledgeBaseTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseTools.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final ToolExecutionRecorder recorder;

    public KnowledgeBaseTools(KnowledgeBaseService knowledgeBaseService, ToolExecutionRecorder recorder) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.recorder = recorder;
    }

    @Tool(name = "searchKnowledgeBase", description = "在当前知识库中检索与用户问题相关的文档片段")
    @McpTool(name = "searchKnowledgeBase", description = "在当前知识库中检索与用户问题相关的文档片段")
    public List<SourceDto> searchKnowledgeBase(
            @ToolParam(description = "知识库 ID")
            @McpToolParam(description = "知识库 ID")
            String knowledgeBaseId,

            @ToolParam(description = "用户问题或检索关键词")
            @McpToolParam(description = "用户问题或检索关键词")
            String query,

            @ToolParam(description = "最多返回的片段数量")
            @McpToolParam(description = "最多返回的片段数量")
            Integer topK
    ) {
        UUID id = UUID.fromString(knowledgeBaseId);
        int effectiveTopK = topK == null || topK <= 0 ? 5 : Math.min(topK, 10);
        log.info(
                "Tool searchKnowledgeBase invoked knowledgeBaseId={} topK={} queryChars={}",
                knowledgeBaseId,
                effectiveTopK,
                query == null ? 0 : query.length()
        );
        List<SourceDto> sources = knowledgeBaseService.search(id, query, effectiveTopK);
        recorder.record(
                "searchKnowledgeBase",
                "{\"knowledgeBaseId\":\"" + knowledgeBaseId + "\",\"query\":\"" + sanitize(query) + "\",\"topK\":" + effectiveTopK + "}",
                "retrievedCount=" + sources.size()
        );
        log.info("Tool searchKnowledgeBase completed knowledgeBaseId={} resultCount={}", knowledgeBaseId, sources.size());
        return sources;
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
