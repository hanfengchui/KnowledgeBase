package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import com.example.knowledgeassistant.dto.AskRequest;
import com.example.knowledgeassistant.dto.KnowledgeBaseSummary;
import com.example.knowledgeassistant.dto.SourceDto;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import com.example.knowledgeassistant.tool.OrderTools;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private KnowledgeBaseService knowledgeBaseService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private PermissionService permissionService;

    @Mock
    private AuditLogService auditLogService;

    private KnowledgeAssistantProperties properties;
    private ToolExecutionRecorder recorder;
    private OrderTools orderTools;
    private KnowledgeBaseSummary knowledgeBase;
    private CurrentUser currentUser;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.defaultSystem(anyString())).thenReturn(chatClientBuilder);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        properties = new KnowledgeAssistantProperties();
        properties.setTopK(5);
        properties.setChatModel("qwen3:8b");
        properties.setEmbeddingModel("bge-m3");

        recorder = new ToolExecutionRecorder();
        orderTools = new OrderTools(recorder);
        knowledgeBase = new KnowledgeBaseSummary(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                "general-operations",
                "通用运营知识库",
                "默认知识库",
                true,
                Instant.parse("2026-05-01T00:00:00Z"),
                3
        );
        currentUser = new CurrentUser(
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                "viewer",
                "知识查看者",
                knowledgeBase.tenantId(),
                "demo",
                Set.of(AuthorizationCatalog.ROLE_KB_VIEWER),
                "token-1"
        );
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        doNothing().when(permissionService).requireKnowledgeBasePermission(currentUser, knowledgeBase.id(), AuthorizationCatalog.PERMISSION_CHAT_ASK);
        when(permissionService.hasKnowledgeBasePermission(currentUser, knowledgeBase.id(), AuthorizationCatalog.PERMISSION_TOOL_QUERY_ORDER)).thenReturn(false);
    }

    @Test
    void returnsEvidenceShortageWhenNoSourcesAndNoToolResults() {
        ChatService service = new ChatService(
                chatClientBuilder,
                knowledgeBaseService,
                properties,
                orderTools,
                recorder,
                currentUserProvider,
                permissionService,
                auditLogService
        );
        AskRequest request = new AskRequest("知识库里有没有班车路线？", 5, knowledgeBase.id());

        when(knowledgeBaseService.resolveKnowledgeBaseOrDefault(knowledgeBase.id())).thenReturn(knowledgeBase);
        when(knowledgeBaseService.search(knowledgeBase.id(), request.question(), 5)).thenReturn(List.of());

        var response = service.ask(request);

        assertThat(response.answer()).isEqualTo("知识库未提供足够依据。");
        assertThat(response.sources()).isEmpty();
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.stats().usedRag()).isFalse();
        assertThat(response.stats().usedTools()).isFalse();
        verify(chatClient, never()).prompt();
    }

    @Test
    void usesToolResultsWhenQuestionContainsOrderNumber() {
        ChatService service = new ChatService(
                chatClientBuilder,
                knowledgeBaseService,
                properties,
                orderTools,
                recorder,
                currentUserProvider,
                permissionService,
                auditLogService
        );
        AskRequest request = new AskRequest("请查询 ORD-2026-0001 当前状态", 5, knowledgeBase.id());

        when(knowledgeBaseService.resolveKnowledgeBaseOrDefault(knowledgeBase.id())).thenReturn(knowledgeBase);
        when(knowledgeBaseService.search(knowledgeBase.id(), request.question(), 5)).thenReturn(List.of());
        when(permissionService.hasKnowledgeBasePermission(currentUser, knowledgeBase.id(), AuthorizationCatalog.PERMISSION_TOOL_QUERY_ORDER)).thenReturn(true);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("订单已发货，预计按计划送达。");
        clearInvocations(chatClient);

        var response = service.ask(request);

        assertThat(response.answer()).contains("已发货");
        assertThat(response.sources()).isEmpty();
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("queryOrder");
        assertThat(response.stats().usedRag()).isFalse();
        assertThat(response.stats().usedTools()).isTrue();
        verify(chatClient).prompt();
    }

    @Test
    void usesRetrievedSourcesWhenKnowledgeBaseHasEvidence() {
        ChatService service = new ChatService(
                chatClientBuilder,
                knowledgeBaseService,
                properties,
                orderTools,
                recorder,
                currentUserProvider,
                permissionService,
                auditLogService
        );
        AskRequest request = new AskRequest("企业标准退款规则是什么？", 3, knowledgeBase.id());

        List<SourceDto> sources = List.of(
                new SourceDto(
                        knowledgeBase.tenantId().toString(),
                        knowledgeBase.id().toString(),
                        knowledgeBase.name(),
                        UUID.randomUUID().toString(),
                        "售后退款交付与支持政策.md",
                        "markdown",
                        0,
                        0.91,
                        "企业客户在合同生效后 7 个自然日内，可以对标准版或专业版订阅提出退款申请。"
                )
        );

        when(knowledgeBaseService.resolveKnowledgeBaseOrDefault(knowledgeBase.id())).thenReturn(knowledgeBase);
        when(knowledgeBaseService.search(knowledgeBase.id(), request.question(), 3)).thenReturn(sources);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("标准规则是合同生效后 7 个自然日内可申请退款。");
        clearInvocations(chatClient);

        var response = service.ask(request);

        assertThat(response.answer()).contains("7 个自然日");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.stats().usedRag()).isTrue();
        assertThat(response.stats().usedTools()).isFalse();
        assertThat(response.stats().knowledgeBaseName()).isEqualTo("通用运营知识库");
        verify(chatClient).prompt();
    }

    @Test
    void skipsToolExecutionWhenUserLacksToolPermission() {
        ChatService service = new ChatService(
                chatClientBuilder,
                knowledgeBaseService,
                properties,
                orderTools,
                recorder,
                currentUserProvider,
                permissionService,
                auditLogService
        );
        AskRequest request = new AskRequest("请查询 ORD-2026-0001 当前状态", 5, knowledgeBase.id());

        when(knowledgeBaseService.resolveKnowledgeBaseOrDefault(knowledgeBase.id())).thenReturn(knowledgeBase);
        when(knowledgeBaseService.search(knowledgeBase.id(), request.question(), 5)).thenReturn(List.of());

        var response = service.ask(request);

        assertThat(response.answer()).isEqualTo("知识库未提供足够依据。");
        assertThat(response.toolCalls()).isEmpty();
        assertThat(response.stats().usedTools()).isFalse();
        verify(chatClient, never()).prompt();
    }
}
