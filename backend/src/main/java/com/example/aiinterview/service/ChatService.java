package com.example.aiinterview.service;

import com.example.aiinterview.config.AiAssistantProperties;
import com.example.aiinterview.dto.AskRequest;
import com.example.aiinterview.dto.AskResponse;
import com.example.aiinterview.dto.ChatStats;
import com.example.aiinterview.dto.OrderInfo;
import com.example.aiinterview.dto.SourceDto;
import com.example.aiinterview.dto.ToolCallDto;
import com.example.aiinterview.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?i)\\bORD-\\d{4}-\\d{4}\\b");

    private static final String SYSTEM_PROMPT = """
            你是一个企业知识库 + 业务查询 AI 助手，用于演示 RAG、Embedding、向量检索、工具调用和结构化输出。
            回答规则：
            1. 优先依据用户消息中的“知识库召回”回答，并尽量引用来源序号。
            2. 如果知识库召回不足以回答，明确说明“知识库未提供足够依据”，不要编造。
            3. 如果用户消息中提供了订单号，优先依据“业务工具结果”回答订单、物流、支付或退款状态。
            4. 输出中文，先给结论，再给依据；保持简洁，可用于面试现场演示。
            5. 不要输出思考过程、推理草稿、<think> 标签或 Thinking 内容。
            """;

    private final ChatClient chatClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AiAssistantProperties properties;
    private final OrderTools orderTools;
    private final ToolExecutionRecorder toolExecutionRecorder;

    public ChatService(
            ChatClient.Builder chatClientBuilder,
            KnowledgeBaseService knowledgeBaseService,
            AiAssistantProperties properties,
            OrderTools orderTools,
            ToolExecutionRecorder toolExecutionRecorder
    ) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.knowledgeBaseService = knowledgeBaseService;
        this.properties = properties;
        this.orderTools = orderTools;
        this.toolExecutionRecorder = toolExecutionRecorder;
    }

    public AskResponse ask(AskRequest request) {
        Instant start = Instant.now();
        String question = request.question().trim();
        int topK = request.topK() == null ? properties.getTopK() : request.topK();

        List<SourceDto> sources = knowledgeBaseService.search(question, topK);

        toolExecutionRecorder.start();
        String answer;
        List<ToolCallDto> toolCalls;
        try {
            List<OrderInfo> orderInfos = queryMentionedOrders(question);
            String userPrompt = buildUserPrompt(question, sources, orderInfos);
            answer = chatClient.prompt()
                    .user(userPrompt)
                    .call()
                    .content();
        } finally {
            toolCalls = toolExecutionRecorder.drain();
        }

        if (!StringUtils.hasText(answer)) {
            answer = "模型未返回有效内容。";
        }

        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        ChatStats stats = new ChatStats(
                latencyMs,
                sources.size(),
                properties.getChatModel(),
                properties.getEmbeddingModel(),
                !sources.isEmpty(),
                !toolCalls.isEmpty()
        );
        return new AskResponse(answer, sources, stats, toolCalls);
    }

    private List<OrderInfo> queryMentionedOrders(String question) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(question);
        Set<String> orderNos = new LinkedHashSet<>();
        while (matcher.find()) {
            orderNos.add(matcher.group().toUpperCase());
        }
        return orderNos.stream()
                .limit(3)
                .map(orderTools::queryOrder)
                .toList();
    }

    private String buildUserPrompt(String question, List<SourceDto> sources, List<OrderInfo> orderInfos) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n")
                .append(question)
                .append("\n\n知识库召回：\n");

        if (sources.isEmpty()) {
            builder.append("无可用召回。\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                SourceDto source = sources.get(i);
                builder.append("[来源").append(i + 1).append("] ")
                        .append("documentName=").append(source.documentName())
                        .append(", chunkIndex=").append(source.chunkIndex())
                        .append(", score=").append(formatScore(source.score()))
                        .append("\n")
                        .append(source.content())
                        .append("\n\n");
            }
        }

        builder.append("业务工具结果：\n");
        if (orderInfos.isEmpty()) {
            builder.append("无工具结果。\n");
        } else {
            for (OrderInfo orderInfo : orderInfos) {
                builder.append("- 订单号：").append(orderInfo.orderNo()).append("\n")
                        .append("  客户：").append(orderInfo.customerName()).append("\n")
                        .append("  状态：").append(orderInfo.status()).append("\n")
                        .append("  金额：").append(orderInfo.paidAmount()).append("\n")
                        .append("  ETA：").append(orderInfo.eta()).append("\n")
                        .append("  事件：").append(orderInfo.events()).append("\n");
            }
        }

        builder.append("""

                请基于以上知识库召回和业务工具结果回答；如果两者都没有依据，请明确说明缺少依据。
                """);
        return builder.toString();
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "n/a";
        }
        return String.format("%.4f", score);
    }
}
