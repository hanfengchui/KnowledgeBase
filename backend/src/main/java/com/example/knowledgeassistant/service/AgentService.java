package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import com.example.knowledgeassistant.dto.AskRequest;
import com.example.knowledgeassistant.dto.AskResponse;
import com.example.knowledgeassistant.dto.ChatStats;
import com.example.knowledgeassistant.dto.KnowledgeBaseSummary;
import com.example.knowledgeassistant.dto.OrderInfo;
import com.example.knowledgeassistant.dto.SourceDto;
import com.example.knowledgeassistant.dto.ToolCallDto;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import com.example.knowledgeassistant.tool.AgentToolRegistry;
import com.example.knowledgeassistant.tool.OrderTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AgentService {

    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?i)\\bORD-\\d{4}-\\d{4}\\b");
    private static final Pattern BUSINESS_STATUS_HINT_PATTERN = Pattern.compile("(?i)(查询|当前状态|订单状态|物流|退款状态|支付状态|发货)");

    private static final String SYSTEM_PROMPT = """
            你是企业知识库与业务助手平台中的轻量业务智能体。
            回答规则：
            1. 优先依据“知识库召回”回答，并尽量用“来源1、来源2”的方式引用依据。
            2. 如果知识库召回不足以支持结论，必须明确回复“知识库未提供足够依据”，不要编造。
            3. 如果有“业务查询结果”，只可基于工具返回内容回答订单、支付、物流或退款状态，不要超出结果做推断。
            4. 当前可用工具由后端按用户权限控制，不能要求用户绕过权限或访问其他租户数据。
            5. 输出中文，先给结论，再给依据，保持专业、简洁、可直接给业务人员阅读。
            6. 不要输出思考过程、推理草稿、<think> 标签或 Thinking 内容。
            """;

    private final ChatClient chatClient;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAssistantProperties properties;
    private final OrderTools orderTools;
    private final AgentToolRegistry agentToolRegistry;
    private final ToolExecutionRecorder toolExecutionRecorder;
    private final AgentRunRecorder agentRunRecorder;
    private final CurrentUserProvider currentUserProvider;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public AgentService(
            ChatClient.Builder chatClientBuilder,
            KnowledgeBaseService knowledgeBaseService,
            KnowledgeAssistantProperties properties,
            OrderTools orderTools,
            AgentToolRegistry agentToolRegistry,
            ToolExecutionRecorder toolExecutionRecorder,
            AgentRunRecorder agentRunRecorder,
            CurrentUserProvider currentUserProvider,
            PermissionService permissionService,
            AuditLogService auditLogService
    ) {
        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT).build();
        this.knowledgeBaseService = knowledgeBaseService;
        this.properties = properties;
        this.orderTools = orderTools;
        this.agentToolRegistry = agentToolRegistry;
        this.toolExecutionRecorder = toolExecutionRecorder;
        this.agentRunRecorder = agentRunRecorder;
        this.currentUserProvider = currentUserProvider;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public AskResponse ask(AskRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        Instant start = Instant.now();
        String question = request.question().trim();
        int topK = request.topK() == null ? properties.getTopK() : request.topK();
        KnowledgeBaseSummary knowledgeBase = knowledgeBaseService.resolveKnowledgeBaseOrDefault(request.knowledgeBaseId());
        AgentRunRecorder.AgentRun run = agentRunRecorder.start(currentUser, knowledgeBase.id(), question);

        permissionService.requireKnowledgeBasePermission(currentUser, knowledgeBase.id(), AuthorizationCatalog.PERMISSION_CHAT_ASK);

        boolean requestedOrderQuery = containsOrderNo(question);
        boolean canUseBusinessTools = currentUser.isPlatformAdmin()
                || permissionService.hasKnowledgeBasePermission(currentUser, knowledgeBase.id(), AuthorizationCatalog.PERMISSION_TOOL_QUERY_ORDER);

        toolExecutionRecorder.start();
        String answer = null;
        List<ToolCallDto> toolCalls = List.of();
        List<SourceDto> sources = List.of();
        List<OrderInfo> orderInfos = List.of();
        String runStatus = "succeeded";
        String errorMessage = null;

        try {
            Instant retrieveStart = Instant.now();
            sources = knowledgeBaseService.search(knowledgeBase.id(), question, topK);
            agentRunRecorder.recordStep(
                    run,
                    "rag",
                    "searchKnowledgeBase",
                    "knowledgeBaseId=" + knowledgeBase.id() + ", topK=" + topK + ", question=" + question,
                    agentRunRecorder.summarizeSources(sources.size()),
                    elapsedMs(retrieveStart),
                    "succeeded"
            );

            if (requestedOrderQuery && canUseBusinessTools) {
                Instant toolStart = Instant.now();
                orderInfos = queryMentionedOrders(question);
                sources = refineSourcesForBusinessStatus(question, orderInfos, sources);
                agentRunRecorder.recordStep(
                        run,
                        "tool",
                        "queryOrder",
                        "question=" + question,
                        "orderCount=" + orderInfos.size(),
                        elapsedMs(toolStart),
                        "succeeded"
                );
            } else if (requestedOrderQuery) {
                agentRunRecorder.recordStep(
                        run,
                        "guardrail",
                        "toolPermissionCheck",
                        "question=" + question,
                        "订单查询工具权限不足，未执行工具调用",
                        0,
                        "skipped"
                );
            }

            if (sources.isEmpty() && orderInfos.isEmpty()) {
                answer = "知识库未提供足够依据。";
                agentRunRecorder.recordStep(
                        run,
                        "guardrail",
                        "evidenceCheck",
                        "retrievedCount=0, toolResultCount=0",
                        answer,
                        0,
                        "succeeded"
                );
            } else {
                String userPrompt = buildUserPrompt(knowledgeBase, question, sources, orderInfos);
                Instant modelStart = Instant.now();
                answer = chatClient.prompt()
                        .toolCallbacks(agentToolRegistry.allowedTools(canUseBusinessTools))
                        .user(userPrompt)
                        .call()
                        .content();
                agentRunRecorder.recordStep(
                        run,
                        "llm",
                        "generateAnswer",
                        "promptChars=" + userPrompt.length() + ", allowedBusinessTools=" + canUseBusinessTools,
                        agentRunRecorder.summarizeText(answer),
                        elapsedMs(modelStart),
                        "succeeded"
                );
            }
        } catch (RuntimeException ex) {
            runStatus = "failed";
            errorMessage = ex.getMessage();
            throw ex;
        } finally {
            toolCalls = toolExecutionRecorder.drain();
            agentRunRecorder.complete(
                    run,
                    !sources.isEmpty(),
                    !toolCalls.isEmpty(),
                    sources.size(),
                    toolCalls.size(),
                    runStatus,
                    errorMessage
            );
        }

        if (!StringUtils.hasText(answer)) {
            answer = sources.isEmpty() ? "知识库未提供足够依据。" : "已检索到相关依据，但当前模型未生成有效回答，请先参考来源片段。";
        }

        recordAudit(currentUser, knowledgeBase, question, requestedOrderQuery, canUseBusinessTools, toolCalls);

        long latencyMs = Duration.between(start, Instant.now()).toMillis();
        ChatStats stats = new ChatStats(
                latencyMs,
                sources.size(),
                properties.getChatModel(),
                properties.getEmbeddingModel(),
                !sources.isEmpty(),
                !toolCalls.isEmpty(),
                knowledgeBase.id(),
                knowledgeBase.name()
        );
        return new AskResponse(
                answer,
                sources,
                stats,
                canUseBusinessTools ? toolCalls : List.of(),
                agentRunRecorder.toTrace(run, runStatus)
        );
    }

    private void recordAudit(
            CurrentUser currentUser,
            KnowledgeBaseSummary knowledgeBase,
            String question,
            boolean requestedOrderQuery,
            boolean canUseBusinessTools,
            List<ToolCallDto> toolCalls
    ) {
        if (requestedOrderQuery && !canUseBusinessTools) {
            auditLogService.record(
                    currentUser,
                    "tool.query_order_denied",
                    "tool",
                    knowledgeBase.id().toString(),
                    "knowledgeBaseId=" + knowledgeBase.id() + ", question=" + auditLogService.summarize(question),
                    HttpStatus.OK.value()
            );
        } else if (!toolCalls.isEmpty()) {
            auditLogService.record(
                    currentUser,
                    "agent.tools_success",
                    "agent",
                    knowledgeBase.id().toString(),
                    "knowledgeBaseId=" + knowledgeBase.id() + ", count=" + toolCalls.size(),
                    HttpStatus.OK.value()
            );
        }

        auditLogService.record(
                currentUser,
                "agent.ask",
                "knowledge_base",
                knowledgeBase.id().toString(),
                "knowledgeBaseId=" + knowledgeBase.id() + ", question=" + auditLogService.summarize(question),
                HttpStatus.OK.value()
        );
    }

    private boolean containsOrderNo(String question) {
        return ORDER_NO_PATTERN.matcher(question).find();
    }

    private List<OrderInfo> queryMentionedOrders(String question) {
        Matcher matcher = ORDER_NO_PATTERN.matcher(question);
        Set<String> orderNos = new LinkedHashSet<>();
        while (matcher.find()) {
            orderNos.add(matcher.group().toUpperCase(Locale.ROOT));
        }
        return orderNos.stream()
                .limit(3)
                .map(orderTools::queryOrder)
                .toList();
    }

    private List<SourceDto> refineSourcesForBusinessStatus(
            String question,
            List<OrderInfo> orderInfos,
            List<SourceDto> sources
    ) {
        if (orderInfos.isEmpty() || sources.isEmpty() || !BUSINESS_STATUS_HINT_PATTERN.matcher(question).find()) {
            return sources;
        }

        Set<String> orderNos = orderInfos.stream()
                .map(OrderInfo::orderNo)
                .filter(StringUtils::hasText)
                .map(orderNo -> orderNo.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());

        return sources.stream()
                .filter(source -> {
                    String content = source.content() == null ? "" : source.content().toUpperCase(Locale.ROOT);
                    return orderNos.stream().anyMatch(content::contains);
                })
                .toList();
    }

    private String buildUserPrompt(
            KnowledgeBaseSummary knowledgeBase,
            String question,
            List<SourceDto> sources,
            List<OrderInfo> orderInfos
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("当前知识库：\n")
                .append(knowledgeBase.name())
                .append(" (")
                .append(knowledgeBase.code())
                .append(")\n\n")
                .append("用户问题：\n")
                .append(question)
                .append("\n\n知识库召回：\n");

        if (sources.isEmpty()) {
            builder.append("无可用召回。\n");
        } else {
            for (int i = 0; i < sources.size(); i++) {
                SourceDto source = sources.get(i);
                builder.append("[来源").append(i + 1).append("] ")
                        .append("knowledgeBase=").append(source.knowledgeBaseName())
                        .append(", documentName=").append(source.documentName())
                        .append(", documentType=").append(source.documentType())
                        .append(", chunkIndex=").append(source.chunkIndex())
                        .append(", score=").append(formatScore(source.score()))
                        .append("\n")
                        .append(source.content())
                        .append("\n\n");
            }
        }

        builder.append("业务查询结果：\n");
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

                请严格基于知识库召回和业务查询结果回答；如果证据仍不足，请直接输出“知识库未提供足够依据”。
                """);
        return builder.toString();
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "n/a";
        }
        return String.format(Locale.ROOT, "%.4f", score);
    }
}
