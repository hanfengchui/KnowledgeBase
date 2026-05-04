package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import com.example.knowledgeassistant.dto.KnowledgeBaseSummary;
import com.example.knowledgeassistant.dto.SourceDto;
import com.example.knowledgeassistant.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeBaseServiceSearchTest {

    @Test
    void keepsOnlySourcesFromRequestedKnowledgeBase() {
        VectorStore vectorStore = mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DocumentChunker chunker = mock(DocumentChunker.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        KnowledgeAssistantProperties properties = new KnowledgeAssistantProperties();
        properties.setVectorEnabled(true);

        UUID requestedKnowledgeBaseId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID otherKnowledgeBaseId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "viewer", "查看者", tenantId, "demo", Set.of("kb_viewer"), "token-1");
        KnowledgeBaseSummary knowledgeBase = new KnowledgeBaseSummary(
                requestedKnowledgeBaseId,
                tenantId,
                "target-kb",
                "目标知识库",
                "测试知识库",
                false,
                Instant.parse("2026-05-01T00:00:00Z"),
                1
        );

        Document requestedDocument = new Document(
                "chunk-1",
                "退款规则",
                Map.of(
                        "tenant_id", tenantId.toString(),
                        "knowledge_base_id", requestedKnowledgeBaseId.toString(),
                        "knowledge_base_name", "目标知识库",
                        "document_id", UUID.randomUUID().toString(),
                        "document_name", "policy.md",
                        "document_type", "markdown",
                        "chunk_index", 0
                )
        );
        Document otherDocument = new Document(
                "chunk-2",
                "其他知识",
                Map.of(
                        "tenant_id", tenantId.toString(),
                        "knowledge_base_id", otherKnowledgeBaseId.toString(),
                        "knowledge_base_name", "其他知识库",
                        "document_id", UUID.randomUUID().toString(),
                        "document_name", "other.md",
                        "document_type", "markdown",
                        "chunk_index", 0
                )
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(requestedDocument, otherDocument));
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(permissionService.canAccessKnowledgeBase(currentUser, requestedKnowledgeBaseId)).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(requestedKnowledgeBaseId), eq(tenantId)))
                .thenReturn(knowledgeBase);

        KnowledgeBaseService service = new KnowledgeBaseService(
                vectorStore,
                jdbcTemplate,
                namedParameterJdbcTemplate,
                chunker,
                extractor,
                properties,
                currentUserProvider,
                permissionService,
                auditLogService
        );

        List<SourceDto> result = service.search(requestedKnowledgeBaseId, "退款规则", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(tenantId.toString());
        assertThat(result.get(0).knowledgeBaseId()).isEqualTo(requestedKnowledgeBaseId.toString());
        assertThat(result.get(0).documentName()).isEqualTo("policy.md");
    }

    @Test
    void fallsBackToEmptyKeywordResultsWhenVectorResultsBelongToAnotherKnowledgeBase() {
        VectorStore vectorStore = mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DocumentChunker chunker = mock(DocumentChunker.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        KnowledgeAssistantProperties properties = new KnowledgeAssistantProperties();
        properties.setVectorEnabled(true);

        UUID requestedKnowledgeBaseId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID otherKnowledgeBaseId = UUID.randomUUID();
        CurrentUser currentUser = new CurrentUser(UUID.randomUUID(), "viewer", "查看者", tenantId, "demo", Set.of("kb_viewer"), "token-1");
        KnowledgeBaseSummary knowledgeBase = new KnowledgeBaseSummary(
                requestedKnowledgeBaseId,
                tenantId,
                "target-kb",
                "目标知识库",
                "测试知识库",
                false,
                Instant.parse("2026-05-01T00:00:00Z"),
                1
        );

        Document otherDocument = new Document(
                "chunk-2",
                "其他知识",
                Map.of(
                        "tenant_id", tenantId.toString(),
                        "knowledge_base_id", otherKnowledgeBaseId.toString(),
                        "knowledge_base_name", "其他知识库",
                        "document_id", UUID.randomUUID().toString(),
                        "document_name", "other.md",
                        "document_type", "markdown",
                        "chunk_index", 0
                )
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(otherDocument));
        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(permissionService.canAccessKnowledgeBase(currentUser, requestedKnowledgeBaseId)).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(requestedKnowledgeBaseId), eq(tenantId)))
                .thenReturn(knowledgeBase);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(tenantId), eq(requestedKnowledgeBaseId), eq("indexed")))
                .thenReturn(List.of());

        KnowledgeBaseService service = new KnowledgeBaseService(
                vectorStore,
                jdbcTemplate,
                namedParameterJdbcTemplate,
                chunker,
                extractor,
                properties,
                currentUserProvider,
                permissionService,
                auditLogService
        );

        List<SourceDto> result = service.search(requestedKnowledgeBaseId, "退款规则", 5);

        assertThat(result).isEmpty();
    }
}
