package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import com.example.knowledgeassistant.dto.KnowledgeBaseSummary;
import com.example.knowledgeassistant.dto.UpdateKnowledgeBaseRequest;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceUpdateTest {

    @Test
    void updatesKnowledgeBaseFieldsWhenUserHasPermission() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        NamedParameterJdbcTemplate namedParameterJdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        DocumentChunker chunker = mock(DocumentChunker.class);
        DocumentContentExtractor extractor = mock(DocumentContentExtractor.class);
        CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
        PermissionService permissionService = mock(PermissionService.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        UUID tenantId = UUID.randomUUID();
        UUID knowledgeBaseId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-05-01T00:00:00Z");
        CurrentUser currentUser = new CurrentUser(
                UUID.randomUUID(),
                "kb-admin",
                "知识库管理员",
                tenantId,
                "demo",
                Set.of(AuthorizationCatalog.ROLE_KB_ADMIN),
                "token-1"
        );

        when(currentUserProvider.getCurrentUser()).thenReturn(currentUser);
        when(jdbcTemplate.queryForObject(contains("is_default = TRUE"), eq(Integer.class), eq(tenantId))).thenReturn(1);
        when(jdbcTemplate.queryForObject(contains("AND id <> ?"), eq(Integer.class), eq(tenantId), eq("after-sales"), eq(knowledgeBaseId)))
                .thenReturn(0);
        when(permissionService.canAccessKnowledgeBase(currentUser, knowledgeBaseId)).thenReturn(true);
        stubKnowledgeBaseLookup(jdbcTemplate, tenantId, knowledgeBaseId, createdAt);

        KnowledgeBaseService service = new KnowledgeBaseService(
                vectorStore,
                jdbcTemplate,
                namedParameterJdbcTemplate,
                chunker,
                extractor,
                new KnowledgeAssistantProperties(),
                currentUserProvider,
                permissionService,
                auditLogService
        );

        KnowledgeBaseSummary result = service.updateKnowledgeBase(
                knowledgeBaseId,
                new UpdateKnowledgeBaseRequest(" 售后知识库 ", "After Sales", " 覆盖售后问题 ")
        );

        assertThat(result.name()).isEqualTo("售后知识库");
        assertThat(result.code()).isEqualTo("after-sales");
        assertThat(result.description()).isEqualTo("覆盖售后问题");
        verify(permissionService).requireKnowledgeBasePermission(currentUser, knowledgeBaseId, AuthorizationCatalog.PERMISSION_KB_UPDATE);
        verify(jdbcTemplate).update(
                contains("UPDATE knowledge_bases"),
                eq("after-sales"),
                eq("售后知识库"),
                eq("覆盖售后问题"),
                eq(knowledgeBaseId),
                eq(tenantId)
        );
        verify(auditLogService).record(
                currentUser,
                "kb.update",
                "knowledge_base",
                knowledgeBaseId.toString(),
                "name=售后知识库, code=after-sales",
                200
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubKnowledgeBaseLookup(
            JdbcTemplate jdbcTemplate,
            UUID tenantId,
            UUID knowledgeBaseId,
            Instant createdAt
    ) throws Exception {
        AtomicInteger lookupCount = new AtomicInteger();
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.any(RowMapper.class), eq(knowledgeBaseId), eq(tenantId)))
                .thenAnswer(invocation -> {
                    RowMapper<KnowledgeBaseSummary> mapper = invocation.getArgument(1);
                    boolean updated = lookupCount.getAndIncrement() > 0;
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.getObject("id", UUID.class)).thenReturn(knowledgeBaseId);
                    when(rs.getObject("tenant_id", UUID.class)).thenReturn(tenantId);
                    when(rs.getString("code")).thenReturn(updated ? "after-sales" : "general");
                    when(rs.getString("name")).thenReturn(updated ? "售后知识库" : "通用知识库");
                    when(rs.getString("description")).thenReturn(updated ? "覆盖售后问题" : "默认知识库");
                    when(rs.getBoolean("is_default")).thenReturn(true);
                    when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(createdAt));
                    when(rs.getLong("document_count")).thenReturn(3L);
                    return mapper.mapRow(rs, 0);
                });
    }
}
