package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import com.example.knowledgeassistant.dto.CreateKnowledgeBaseRequest;
import com.example.knowledgeassistant.dto.DocumentSummary;
import com.example.knowledgeassistant.dto.KnowledgeBaseSummary;
import com.example.knowledgeassistant.dto.ReindexResponse;
import com.example.knowledgeassistant.dto.SourceDto;
import com.example.knowledgeassistant.dto.UpdateKnowledgeBaseRequest;
import com.example.knowledgeassistant.dto.UploadResponse;
import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionTextParser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_INDEXING = "indexing";
    private static final String STATUS_INDEXED = "indexed";
    private static final String STATUS_FAILED = "failed";
    private static final int MAX_CODE_LENGTH = 80;

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    private final DocumentChunker documentChunker;
    private final DocumentContentExtractor documentContentExtractor;
    private final KnowledgeAssistantProperties properties;
    private final CurrentUserProvider currentUserProvider;
    private final PermissionService permissionService;
    private final AuditLogService auditLogService;

    public KnowledgeBaseService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            NamedParameterJdbcTemplate namedParameterJdbcTemplate,
            DocumentChunker documentChunker,
            DocumentContentExtractor documentContentExtractor,
            KnowledgeAssistantProperties properties,
            CurrentUserProvider currentUserProvider,
            PermissionService permissionService,
            AuditLogService auditLogService
    ) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.namedParameterJdbcTemplate = namedParameterJdbcTemplate;
        this.documentChunker = documentChunker;
        this.documentContentExtractor = documentContentExtractor;
        this.properties = properties;
        this.currentUserProvider = currentUserProvider;
        this.permissionService = permissionService;
        this.auditLogService = auditLogService;
    }

    public List<KnowledgeBaseSummary> listKnowledgeBases() {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ensureTenantDefaultKnowledgeBase(currentUser.tenantId());

        return queryTenantKnowledgeBases(currentUser).stream()
                .filter(kb -> permissionService.canAccessKnowledgeBase(currentUser, kb.id()))
                .toList();
    }

    @Transactional
    public KnowledgeBaseSummary createKnowledgeBase(CreateKnowledgeBaseRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        permissionService.requireTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_KB_CREATE);
        UUID tenantId = requireTenantScope(currentUser);
        ensureTenantDefaultKnowledgeBase(tenantId);

        String name = request.name().trim();
        String baseCode = StringUtils.hasText(request.code()) ? normalizeCode(request.code()) : normalizeCode(name);
        if (!StringUtils.hasText(baseCode)) {
            baseCode = "kb-" + UUID.randomUUID().toString().substring(0, 8);
        }

        UUID id = UUID.randomUUID();
        String code = nextAvailableCode(tenantId, baseCode);
        boolean defaultKnowledgeBase = tenantKnowledgeBaseCount(tenantId) == 0;
        jdbcTemplate.update("""
                INSERT INTO knowledge_bases (id, tenant_id, code, name, description, is_default)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, tenantId, code, name, nullableText(request.description()), defaultKnowledgeBase);

        KnowledgeBaseSummary summary = getKnowledgeBase(id);
        auditLogService.record(
                currentUser,
                "kb.create",
                "knowledge_base",
                id.toString(),
                "name=" + summary.name() + ", code=" + summary.code(),
                HttpStatus.OK.value()
        );
        return summary;
    }

    @Transactional
    public KnowledgeBaseSummary updateKnowledgeBase(UUID knowledgeBaseId, UpdateKnowledgeBaseRequest request) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ensureTenantDefaultKnowledgeBase(currentUser.tenantId());

        KnowledgeBaseSummary existing = findKnowledgeBaseInTenant(currentUser, knowledgeBaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定知识库"));
        permissionService.requireKnowledgeBasePermission(currentUser, existing.id(), AuthorizationCatalog.PERMISSION_KB_UPDATE);

        String name = request.name().trim();
        String baseCode = StringUtils.hasText(request.code()) ? normalizeCode(request.code()) : normalizeCode(name);
        if (!StringUtils.hasText(baseCode)) {
            baseCode = "kb-" + existing.id().toString().substring(0, 8);
        }

        String code = nextAvailableCode(existing.tenantId(), baseCode, existing.id());
        jdbcTemplate.update("""
                UPDATE knowledge_bases
                SET code = ?,
                    name = ?,
                    description = ?
                WHERE id = ?
                  AND tenant_id = ?
                """, code, name, nullableText(request.description()), existing.id(), existing.tenantId());

        KnowledgeBaseSummary summary = getKnowledgeBase(existing.id());
        auditLogService.record(
                currentUser,
                "kb.update",
                "knowledge_base",
                existing.id().toString(),
                "name=" + summary.name() + ", code=" + summary.code(),
                HttpStatus.OK.value()
        );
        return summary;
    }

    public KnowledgeBaseSummary getKnowledgeBase(UUID knowledgeBaseId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ensureTenantDefaultKnowledgeBase(currentUser.tenantId());
        KnowledgeBaseSummary summary = findKnowledgeBaseInTenant(currentUser, knowledgeBaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到指定知识库"));
        if (!permissionService.canAccessKnowledgeBase(currentUser, summary.id())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号无权访问指定知识库");
        }
        return summary;
    }

    public KnowledgeBaseSummary resolveKnowledgeBaseOrDefault(UUID knowledgeBaseId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ensureTenantDefaultKnowledgeBase(currentUser.tenantId());

        if (knowledgeBaseId != null) {
            return getKnowledgeBase(knowledgeBaseId);
        }

        return queryTenantKnowledgeBases(currentUser).stream()
                .filter(kb -> permissionService.canAccessKnowledgeBase(currentUser, kb.id()))
                .sorted(Comparator.comparing(KnowledgeBaseSummary::isDefault).reversed().thenComparing(KnowledgeBaseSummary::createdAt))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号没有可访问的知识库"));
    }

    @Transactional
    public UploadResponse ingest(MultipartFile file, UUID knowledgeBaseId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        KnowledgeBaseSummary knowledgeBase = resolveKnowledgeBaseOrDefault(knowledgeBaseId);
        requireDocumentUploadPermission(currentUser, knowledgeBase.id());

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }

        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        String documentType = detectDocumentType(fileName);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "读取上传文件失败", ex);
        }

        UUID documentId = UUID.randomUUID();
        String contentType = Optional.ofNullable(file.getContentType()).orElse("application/octet-stream");
        String contentHash = sha256(bytes);

        jdbcTemplate.update("""
                INSERT INTO kb_documents (
                    id, tenant_id, knowledge_base_id, file_name, content_type, document_type,
                    content_hash, char_count, chunk_count, status, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 0, ?, NULL)
                """, documentId, knowledgeBase.tenantId(), knowledgeBase.id(), fileName, contentType, documentType, contentHash, STATUS_PENDING);

        try {
            updateDocumentStatus(documentId, STATUS_INDEXING, null, 0, 0);

            DocumentParserResult parsed = documentContentExtractor.extract(fileName, bytes);
            List<TextChunk> chunks = documentChunker.chunk(parsed.text());
            List<Document> aiDocuments = saveChunks(documentId, knowledgeBase, fileName, parsed.documentType(), contentHash, chunks);

            String message = "文档已完成入库，可用于知识问答。";
            if (properties.isVectorEnabled()) {
                try {
                    addToVectorStore(aiDocuments);
                } catch (RuntimeException ex) {
                    message = "文档已入库，当前向量写入不可用，系统将自动使用关键词检索兜底。";
                    log.warn("Vector store add failed, falling back to keyword retrieval: {}", rootMessage(ex));
                }
            } else {
                message = "文档已入库，当前未启用向量检索，系统将使用关键词检索。";
            }

            updateDocumentStatus(documentId, STATUS_INDEXED, null, parsed.text().length(), chunks.size());
            auditLogService.record(
                    currentUser,
                    "document.upload",
                    "document",
                    documentId.toString(),
                    "knowledgeBaseId=" + knowledgeBase.id() + ", fileName=" + fileName + ", chunkCount=" + chunks.size(),
                    HttpStatus.OK.value()
            );
            return new UploadResponse(
                    documentId,
                    knowledgeBase.id(),
                    knowledgeBase.name(),
                    fileName,
                    parsed.documentType(),
                    STATUS_INDEXED,
                    chunks.size(),
                    message
            );
        } catch (DocumentParseException ex) {
            cleanupDocumentChunks(documentId);
            updateDocumentStatus(documentId, STATUS_FAILED, ex.getMessage(), 0, 0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            cleanupDocumentChunks(documentId);
            String message = "文档内容为空，无法建立索引";
            updateDocumentStatus(documentId, STATUS_FAILED, message, 0, 0);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message, ex);
        } catch (RuntimeException ex) {
            cleanupDocumentChunks(documentId);
            String errorMessage = "文档入库失败: " + rootMessage(ex);
            updateDocumentStatus(documentId, STATUS_FAILED, errorMessage, 0, 0);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文档入库失败，请稍后重试", ex);
        }
    }

    public List<DocumentSummary> listDocuments(UUID knowledgeBaseId) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        ensureTenantDefaultKnowledgeBase(currentUser.tenantId());

        if (knowledgeBaseId != null) {
            KnowledgeBaseSummary knowledgeBase = getKnowledgeBase(knowledgeBaseId);
            return jdbcTemplate.query("""
                    SELECT d.id,
                           d.knowledge_base_id,
                           kb.name AS knowledge_base_name,
                           d.file_name,
                           d.document_type,
                           d.status,
                           d.chunk_count,
                           d.char_count,
                           d.created_at,
                           d.updated_at,
                           d.error_message,
                           d.content_hash
                    FROM kb_documents d
                    JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
                    WHERE d.tenant_id = ?
                      AND d.knowledge_base_id = ?
                    ORDER BY d.created_at DESC
                    LIMIT 100
                    """, (rs, rowNum) -> toDocumentSummary(rs), knowledgeBase.tenantId(), knowledgeBase.id());
        }

        List<UUID> accessibleKnowledgeBaseIds = listKnowledgeBases().stream()
                .map(KnowledgeBaseSummary::id)
                .toList();
        if (accessibleKnowledgeBaseIds.isEmpty()) {
            return List.of();
        }

        return namedParameterJdbcTemplate.query("""
                SELECT d.id,
                       d.knowledge_base_id,
                       kb.name AS knowledge_base_name,
                       d.file_name,
                       d.document_type,
                       d.status,
                       d.chunk_count,
                       d.char_count,
                       d.created_at,
                       d.updated_at,
                       d.error_message,
                       d.content_hash
                FROM kb_documents d
                JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
                WHERE d.tenant_id = :tenantId
                  AND d.knowledge_base_id IN (:knowledgeBaseIds)
                ORDER BY d.created_at DESC
                LIMIT 100
                """, new MapSqlParameterSource()
                .addValue("tenantId", requireTenantScope(currentUser))
                .addValue("knowledgeBaseIds", accessibleKnowledgeBaseIds), (rs, rowNum) -> toDocumentSummary(rs));
    }

    public List<SourceDto> search(UUID knowledgeBaseId, String query, int topK) {
        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        KnowledgeBaseSummary knowledgeBase = getKnowledgeBase(knowledgeBaseId);
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        int effectiveTopK = topK > 0 ? topK : properties.getTopK();
        if (!properties.isVectorEnabled()) {
            return keywordSearch(knowledgeBase, query, effectiveTopK);
        }

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK)
                .similarityThreshold(properties.getSimilarityThreshold())
                .filterExpression("tenant_id == '" + knowledgeBase.tenantId() + "' && knowledge_base_id == '" + knowledgeBase.id() + "'")
                .build();

        try {
            List<SourceDto> vectorResults = vectorStore.similaritySearch(request).stream()
                    .map(document -> toSourceDto(document, knowledgeBase.name()))
                    .filter(source -> knowledgeBase.id().toString().equals(source.knowledgeBaseId()))
                    .filter(source -> knowledgeBase.tenantId().toString().equals(source.tenantId()))
                    .toList();
            if (!vectorResults.isEmpty()) {
                return vectorResults;
            }
        } catch (RuntimeException ex) {
            log.warn("Vector search failed, falling back to keyword retrieval: {}", rootMessage(ex));
        }

        return keywordSearch(knowledgeBase, query, effectiveTopK);
    }

    @Transactional
    public ReindexResponse rebuildVectorIndex(UUID knowledgeBaseId) {
        if (!properties.isVectorEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前未启用向量检索");
        }

        CurrentUser currentUser = currentUserProvider.getCurrentUser();
        KnowledgeBaseSummary knowledgeBase = knowledgeBaseId == null ? null : getKnowledgeBase(knowledgeBaseId);
        if (knowledgeBase == null) {
            if (!currentUser.isPlatformAdmin() && !currentUser.hasRole(AuthorizationCatalog.ROLE_TENANT_ADMIN)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅平台管理员或租户管理员可执行租户级全量重建");
            }
        } else {
            requireDocumentReindexPermission(currentUser, knowledgeBase.id());
        }

        String vectorTableName = validateVectorTableName(properties.getVectorTableName());
        List<Document> documents = queryDocumentsForReindex(currentUser, knowledgeBase);
        if (documents.isEmpty()) {
            String message = knowledgeBase == null ? "当前没有可重建索引的文档分片。" : "当前知识库下没有可重建索引的文档分片。";
            return new ReindexResponse(0, knowledgeBaseId, knowledgeBase == null ? null : knowledgeBase.name(), vectorTableName, message);
        }

        if (knowledgeBase == null) {
            vectorStore.delete(new FilterExpressionTextParser().parse("tenant_id == '" + requireTenantScope(currentUser) + "'"));
        } else {
            vectorStore.delete(new FilterExpressionTextParser().parse(
                    "tenant_id == '" + knowledgeBase.tenantId() + "' && knowledge_base_id == '" + knowledgeBase.id() + "'"
            ));
        }

        addToVectorStore(documents);
        auditLogService.record(
                currentUser,
                "document.reindex",
                "knowledge_base",
                knowledgeBase == null ? requireTenantScope(currentUser).toString() : knowledgeBase.id().toString(),
                "knowledgeBaseId=" + (knowledgeBase == null ? "ALL" : knowledgeBase.id()) + ", chunkCount=" + documents.size(),
                HttpStatus.OK.value()
        );
        String message = knowledgeBase == null ? "当前租户向量索引已重建。" : "当前知识库向量索引已重建。";
        return new ReindexResponse(documents.size(), knowledgeBaseId, knowledgeBase == null ? null : knowledgeBase.name(), vectorTableName, message);
    }

    public void ensureDefaultKnowledgeBaseForTenant(UUID tenantId) {
        ensureTenantDefaultKnowledgeBase(tenantId);
    }

    private void ensureTenantDefaultKnowledgeBase(UUID tenantId) {
        if (tenantId == null) {
            return;
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_bases WHERE tenant_id = ? AND is_default = TRUE",
                Integer.class,
                tenantId
        );
        if (count != null && count > 0) {
            return;
        }

        Integer any = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_bases WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        if (any == null || any == 0) {
            jdbcTemplate.update("""
                    INSERT INTO knowledge_bases (id, tenant_id, code, name, description, is_default)
                    VALUES (?, ?, 'general-operations', '通用运营知识库', '用于产品说明、政策流程、实施接入和常见问题的默认知识库。', TRUE)
                    """,
                    UUID.randomUUID(),
                    tenantId
            );
            return;
        }

        jdbcTemplate.update("""
                UPDATE knowledge_bases
                SET is_default = TRUE
                WHERE id = (
                    SELECT id
                    FROM knowledge_bases
                    WHERE tenant_id = ?
                    ORDER BY created_at ASC
                    LIMIT 1
                )
                """,
                tenantId
        );
    }

    private List<KnowledgeBaseSummary> queryTenantKnowledgeBases(CurrentUser currentUser) {
        UUID tenantId = requireTenantScope(currentUser);
        return jdbcTemplate.query("""
                SELECT kb.id,
                       kb.tenant_id,
                       kb.code,
                       kb.name,
                       kb.description,
                       kb.is_default,
                       kb.created_at,
                       COUNT(d.id) AS document_count
                FROM knowledge_bases kb
                LEFT JOIN kb_documents d ON d.knowledge_base_id = kb.id
                WHERE kb.tenant_id = ?
                GROUP BY kb.id, kb.tenant_id, kb.code, kb.name, kb.description, kb.is_default, kb.created_at
                ORDER BY kb.is_default DESC, kb.created_at ASC
                """, (rs, rowNum) -> mapKnowledgeBaseSummary(rs), tenantId);
    }

    private Optional<KnowledgeBaseSummary> findKnowledgeBaseInTenant(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            return Optional.empty();
        }

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT kb.id,
                           kb.tenant_id,
                           kb.code,
                           kb.name,
                           kb.description,
                           kb.is_default,
                           kb.created_at,
                           COUNT(d.id) AS document_count
                    FROM knowledge_bases kb
                    LEFT JOIN kb_documents d ON d.knowledge_base_id = kb.id
                    WHERE kb.id = ?
                      AND kb.tenant_id = ?
                    GROUP BY kb.id, kb.tenant_id, kb.code, kb.name, kb.description, kb.is_default, kb.created_at
                    """, (rs, rowNum) -> mapKnowledgeBaseSummary(rs), knowledgeBaseId, requireTenantScope(currentUser)));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    private KnowledgeBaseSummary mapKnowledgeBaseSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new KnowledgeBaseSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("tenant_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getBoolean("is_default"),
                Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toInstant).orElse(null),
                rs.getLong("document_count")
        );
    }

    private List<Document> queryDocumentsForReindex(CurrentUser currentUser, KnowledgeBaseSummary knowledgeBase) {
        StringBuilder sql = new StringBuilder("""
                SELECT c.id AS chunk_id,
                       c.document_id,
                       d.file_name,
                       d.document_type,
                       d.content_hash,
                       c.chunk_index,
                       c.content,
                       kb.id AS knowledge_base_id,
                       kb.tenant_id,
                       kb.name AS knowledge_base_name
                FROM kb_chunks c
                JOIN kb_documents d ON d.id = c.document_id
                JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
                WHERE d.status = :status
                  AND d.tenant_id = :tenantId
                """);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("status", STATUS_INDEXED)
                .addValue("tenantId", requireTenantScope(currentUser));

        if (knowledgeBase != null) {
            sql.append(" AND d.knowledge_base_id = :knowledgeBaseId");
            params.addValue("knowledgeBaseId", knowledgeBase.id());
        } else {
            List<UUID> accessibleIds = listKnowledgeBases().stream().map(KnowledgeBaseSummary::id).toList();
            if (accessibleIds.isEmpty()) {
                return List.of();
            }
            sql.append(" AND d.knowledge_base_id IN (:knowledgeBaseIds)");
            params.addValue("knowledgeBaseIds", accessibleIds);
        }

        sql.append(" ORDER BY d.created_at DESC, c.chunk_index ASC");
        return namedParameterJdbcTemplate.query(sql.toString(), params, (rs, rowNum) -> new Document(
                rs.getObject("chunk_id", UUID.class).toString(),
                rs.getString("content"),
                Map.of(
                        "tenant_id", rs.getObject("tenant_id", UUID.class).toString(),
                        "knowledge_base_id", rs.getObject("knowledge_base_id", UUID.class).toString(),
                        "knowledge_base_name", rs.getString("knowledge_base_name"),
                        "document_id", rs.getObject("document_id", UUID.class).toString(),
                        "document_name", rs.getString("file_name"),
                        "document_type", rs.getString("document_type"),
                        "chunk_id", rs.getObject("chunk_id", UUID.class).toString(),
                        "chunk_index", rs.getInt("chunk_index"),
                        "content_hash", rs.getString("content_hash"),
                        "source", "reindex"
                )
        ));
    }

    private void addToVectorStore(List<Document> documents) {
        for (int start = 0; start < documents.size(); start += 50) {
            int end = Math.min(start + 50, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
    }

    private List<Document> saveChunks(
            UUID documentId,
            KnowledgeBaseSummary knowledgeBase,
            String fileName,
            String documentType,
            String contentHash,
            List<TextChunk> chunks
    ) {
        List<Document> aiDocuments = new ArrayList<>();
        for (TextChunk chunk : chunks) {
            UUID chunkId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO kb_chunks (id, document_id, chunk_index, content, char_count)
                    VALUES (?, ?, ?, ?, ?)
                    """, chunkId, documentId, chunk.index(), chunk.content(), chunk.content().length());

            Map<String, Object> metadata = Map.of(
                    "tenant_id", knowledgeBase.tenantId().toString(),
                    "knowledge_base_id", knowledgeBase.id().toString(),
                    "knowledge_base_name", knowledgeBase.name(),
                    "document_id", documentId.toString(),
                    "document_name", fileName,
                    "document_type", documentType,
                    "chunk_id", chunkId.toString(),
                    "chunk_index", chunk.index(),
                    "content_hash", contentHash,
                    "source", "upload"
            );
            aiDocuments.add(new Document(chunkId.toString(), chunk.content(), metadata));
        }
        return aiDocuments;
    }

    private void cleanupDocumentChunks(UUID documentId) {
        jdbcTemplate.update("DELETE FROM kb_chunks WHERE document_id = ?", documentId);
    }

    private void updateDocumentStatus(UUID documentId, String status, String errorMessage, int charCount, int chunkCount) {
        jdbcTemplate.update("""
                UPDATE kb_documents
                SET status = ?,
                    error_message = ?,
                    char_count = ?,
                    chunk_count = ?,
                    updated_at = now()
                WHERE id = ?
                """, status, nullableText(errorMessage), charCount, chunkCount, documentId);
    }

    private DocumentSummary toDocumentSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new DocumentSummary(
                rs.getObject("id", UUID.class),
                rs.getObject("knowledge_base_id", UUID.class),
                rs.getString("knowledge_base_name"),
                rs.getString("file_name"),
                rs.getString("document_type"),
                rs.getString("status"),
                rs.getInt("chunk_count"),
                rs.getInt("char_count"),
                Optional.ofNullable(rs.getTimestamp("created_at")).map(Timestamp::toInstant).orElse(null),
                Optional.ofNullable(rs.getTimestamp("updated_at")).map(Timestamp::toInstant).orElse(null),
                rs.getString("error_message"),
                rs.getString("content_hash")
        );
    }

    private SourceDto toSourceDto(Document document, String knowledgeBaseName) {
        Map<String, Object> metadata = document.getMetadata();
        return new SourceDto(
                stringValue(metadata.get("tenant_id")),
                stringValue(metadata.get("knowledge_base_id")),
                StringUtils.hasText(knowledgeBaseName) ? knowledgeBaseName : stringValue(metadata.get("knowledge_base_name")),
                stringValue(metadata.get("document_id")),
                stringValue(metadata.get("document_name")),
                stringValue(metadata.get("document_type")),
                integerValue(metadata.get("chunk_index")),
                document.getScore(),
                document.getText()
        );
    }

    private List<SourceDto> keywordSearch(KnowledgeBaseSummary knowledgeBase, String query, int topK) {
        List<SourceDto> candidates = jdbcTemplate.query("""
                SELECT kb.tenant_id,
                       kb.id AS knowledge_base_id,
                       kb.name AS knowledge_base_name,
                       c.document_id,
                       d.file_name,
                       d.document_type,
                       c.chunk_index,
                       c.content
                FROM kb_chunks c
                JOIN kb_documents d ON d.id = c.document_id
                JOIN knowledge_bases kb ON kb.id = d.knowledge_base_id
                WHERE d.tenant_id = ?
                  AND d.knowledge_base_id = ?
                  AND d.status = ?
                ORDER BY d.updated_at DESC, c.chunk_index ASC
                LIMIT 400
                """, (rs, rowNum) -> new SourceDto(
                rs.getString("tenant_id"),
                rs.getString("knowledge_base_id"),
                rs.getString("knowledge_base_name"),
                rs.getString("document_id"),
                rs.getString("file_name"),
                rs.getString("document_type"),
                rs.getInt("chunk_index"),
                null,
                rs.getString("content")
        ), knowledgeBase.tenantId(), knowledgeBase.id(), STATUS_INDEXED);

        return candidates.stream()
                .map(source -> new ScoredSource(source, keywordScore(query, source.content())))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredSource::score).reversed())
                .limit(topK)
                .map(scored -> new SourceDto(
                        scored.source().tenantId(),
                        scored.source().knowledgeBaseId(),
                        scored.source().knowledgeBaseName(),
                        scored.source().documentId(),
                        scored.source().documentName(),
                        scored.source().documentType(),
                        scored.source().chunkIndex(),
                        scored.score(),
                        scored.source().content()
                ))
                .toList();
    }

    private double keywordScore(String query, String content) {
        String normalizedQuery = normalizeForScore(query);
        String normalizedContent = normalizeForScore(content);
        if (normalizedQuery.isBlank() || normalizedContent.isBlank()) {
            return 0;
        }

        double score = 0;
        int tokenCount = 0;
        for (String token : normalizedQuery.split("\\s+")) {
            if (token.length() >= 2) {
                tokenCount++;
                if (normalizedContent.contains(token)) {
                    score += token.length();
                }
            }
        }

        for (int i = 0; i < normalizedQuery.length() - 1; i++) {
            String gram = normalizedQuery.substring(i, i + 2).trim();
            if (gram.length() == 2) {
                tokenCount++;
                if (normalizedContent.contains(gram)) {
                    score += 1.5;
                }
            }
        }

        return tokenCount == 0 ? 0 : score / tokenCount;
    }

    private String normalizeForScore(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}，。！？、；：“”‘’（）【】《》]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String validateVectorTableName(String tableName) {
        if (tableName == null || !tableName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalStateException("Invalid vector table name: " + tableName);
        }
        return tableName;
    }

    private String detectDocumentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt")) {
            return "text";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".docx")) {
            return "docx";
        }
        throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "仅支持 .txt、.md、.pdf、.docx 文档");
    }

    private String nextAvailableCode(UUID tenantId, String baseCode) {
        return nextAvailableCode(tenantId, baseCode, null);
    }

    private String nextAvailableCode(UUID tenantId, String baseCode, UUID excludedKnowledgeBaseId) {
        String candidate = baseCode;
        int suffix = 2;
        while (codeExists(tenantId, candidate, excludedKnowledgeBaseId)) {
            String suffixText = "-" + suffix;
            String prefix = baseCode;
            if (prefix.length() + suffixText.length() > MAX_CODE_LENGTH) {
                prefix = prefix.substring(0, Math.max(1, MAX_CODE_LENGTH - suffixText.length()))
                        .replaceAll("-+$", "");
            }
            candidate = prefix + suffixText;
            suffix++;
        }
        return candidate;
    }

    private boolean codeExists(UUID tenantId, String code) {
        return codeExists(tenantId, code, null);
    }

    private boolean codeExists(UUID tenantId, String code, UUID excludedKnowledgeBaseId) {
        if (excludedKnowledgeBaseId == null) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM knowledge_bases WHERE tenant_id = ? AND code = ?) THEN 1 ELSE 0 END",
                    Integer.class,
                    tenantId,
                    code
            );
            return exists != null && exists == 1;
        }

        Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT CASE WHEN EXISTS (
                    SELECT 1
                    FROM knowledge_bases
                    WHERE tenant_id = ?
                      AND code = ?
                      AND id <> ?
                ) THEN 1 ELSE 0 END
                """,
                Integer.class,
                tenantId,
                code,
                excludedKnowledgeBaseId
        );
        return exists != null && exists == 1;
    }

    private int tenantKnowledgeBaseCount(UUID tenantId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_bases WHERE tenant_id = ?",
                Integer.class,
                tenantId
        );
        return count == null ? 0 : count;
    }

    private String normalizeCode(String raw) {
        String normalized = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        if (normalized.length() > MAX_CODE_LENGTH) {
            normalized = normalized.substring(0, MAX_CODE_LENGTH).replaceAll("-+$", "");
        }
        return normalized;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        return Integer.parseInt(value.toString());
    }

    private String nullableText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private UUID requireTenantScope(CurrentUser currentUser) {
        if (currentUser.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号未绑定租户上下文");
        }
        return currentUser.tenantId();
    }

    private void requireDocumentUploadPermission(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (currentUser.isPlatformAdmin() || currentUser.hasRole(AuthorizationCatalog.ROLE_TENANT_ADMIN)) {
            return;
        }
        permissionService.requireKnowledgeBasePermission(currentUser, knowledgeBaseId, AuthorizationCatalog.PERMISSION_DOCUMENT_UPLOAD);
    }

    private void requireDocumentReindexPermission(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (currentUser.isPlatformAdmin() || currentUser.hasRole(AuthorizationCatalog.ROLE_TENANT_ADMIN)) {
            return;
        }
        permissionService.requireKnowledgeBasePermission(currentUser, knowledgeBaseId, AuthorizationCatalog.PERMISSION_DOCUMENT_REINDEX);
    }

    private record ScoredSource(SourceDto source, double score) {
    }
}
