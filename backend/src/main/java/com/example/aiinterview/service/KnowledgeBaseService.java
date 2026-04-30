package com.example.aiinterview.service;

import com.example.aiinterview.config.AiAssistantProperties;
import com.example.aiinterview.dto.DocumentSummary;
import com.example.aiinterview.dto.ReindexResponse;
import com.example.aiinterview.dto.SourceDto;
import com.example.aiinterview.dto.UploadResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Comparator;

@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final DocumentChunker documentChunker;
    private final AiAssistantProperties properties;

    public KnowledgeBaseService(
            VectorStore vectorStore,
            JdbcTemplate jdbcTemplate,
            DocumentChunker documentChunker,
            AiAssistantProperties properties
    ) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.documentChunker = documentChunker;
        this.properties = properties;
    }

    @Transactional
    public UploadResponse ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file must not be empty");
        }

        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("document.txt");
        validateExtension(fileName);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failed to read uploaded file", ex);
        }

        String text = new String(bytes, StandardCharsets.UTF_8);
        List<TextChunk> chunks;
        try {
            chunks = documentChunker.chunk(text);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }

        UUID documentId = UUID.randomUUID();
        String contentHash = sha256(bytes);
        String contentType = Optional.ofNullable(file.getContentType()).orElse("text/plain");

        jdbcTemplate.update("""
                INSERT INTO kb_documents (id, file_name, content_type, content_hash, char_count, chunk_count)
                VALUES (?, ?, ?, ?, ?, ?)
                """, documentId, fileName, contentType, contentHash, text.length(), chunks.size());

        List<Document> aiDocuments = new ArrayList<>();
        for (TextChunk chunk : chunks) {
            UUID chunkId = UUID.randomUUID();
            jdbcTemplate.update("""
                    INSERT INTO kb_chunks (id, document_id, chunk_index, content, char_count)
                    VALUES (?, ?, ?, ?, ?)
                    """, chunkId, documentId, chunk.index(), chunk.content(), chunk.content().length());

            Map<String, Object> metadata = Map.of(
                    "document_id", documentId.toString(),
                    "document_name", fileName,
                    "chunk_id", chunkId.toString(),
                    "chunk_index", chunk.index(),
                    "source", "upload",
                    "content_hash", contentHash
            );
            aiDocuments.add(new Document(chunkId.toString(), chunk.content(), metadata));
        }

        String message = "document ingested";
        if (properties.isVectorEnabled()) {
            try {
                vectorStore.add(aiDocuments);
            } catch (RuntimeException ex) {
                message = "document ingested; vector embedding unavailable, keyword fallback will be used";
                log.warn("Vector store add failed, document chunks were saved and keyword fallback will be used: {}",
                        rootMessage(ex));
            }
        } else {
            message = "document ingested; vector disabled, keyword fallback will be used";
        }
        return new UploadResponse(documentId, fileName, chunks.size(), message);
    }

    public List<SourceDto> search(String query, int topK) {
        int effectiveTopK = topK > 0 ? topK : properties.getTopK();
        if (!properties.isVectorEnabled()) {
            return keywordSearch(query, effectiveTopK);
        }

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(effectiveTopK)
                .similarityThreshold(properties.getSimilarityThreshold())
                .build();

        try {
            return vectorStore.similaritySearch(request).stream()
                    .map(this::toSourceDto)
                    .toList();
        } catch (RuntimeException ex) {
            log.warn("Vector search failed, falling back to keyword search: {}", rootMessage(ex));
            return keywordSearch(query, effectiveTopK);
        }
    }

    @Transactional
    public ReindexResponse rebuildVectorIndex() {
        if (!properties.isVectorEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vector search is disabled");
        }

        String vectorTableName = validateVectorTableName(properties.getVectorTableName());
        List<Document> documents = jdbcTemplate.query("""
                SELECT c.id AS chunk_id, c.document_id, d.file_name, d.content_hash, c.chunk_index, c.content
                FROM kb_chunks c
                JOIN kb_documents d ON d.id = c.document_id
                ORDER BY d.created_at DESC, c.chunk_index ASC
                """, (rs, rowNum) -> new Document(
                rs.getObject("chunk_id", UUID.class).toString(),
                rs.getString("content"),
                Map.of(
                        "document_id", rs.getObject("document_id", UUID.class).toString(),
                        "document_name", rs.getString("file_name"),
                        "chunk_id", rs.getObject("chunk_id", UUID.class).toString(),
                        "chunk_index", rs.getInt("chunk_index"),
                        "source", "reindex",
                        "content_hash", rs.getString("content_hash")
                )
        ));

        if (documents.isEmpty()) {
            return new ReindexResponse(0, vectorTableName, "no chunks to reindex");
        }

        jdbcTemplate.execute("TRUNCATE TABLE " + vectorTableName);
        for (int start = 0; start < documents.size(); start += 50) {
            int end = Math.min(start + 50, documents.size());
            vectorStore.add(documents.subList(start, end));
        }
        return new ReindexResponse(documents.size(), vectorTableName, "vector index rebuilt");
    }

    public List<DocumentSummary> listDocuments() {
        return jdbcTemplate.query("""
                SELECT id, file_name, chunk_count, created_at, content_hash
                FROM kb_documents
                ORDER BY created_at DESC
                LIMIT 50
                """, (rs, rowNum) -> new DocumentSummary(
                rs.getObject("id", UUID.class),
                rs.getString("file_name"),
                rs.getInt("chunk_count"),
                Optional.ofNullable(rs.getTimestamp("created_at"))
                        .map(Timestamp::toInstant)
                        .orElse(null),
                rs.getString("content_hash")
        ));
    }

    private SourceDto toSourceDto(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new SourceDto(
                stringValue(metadata.get("document_id")),
                stringValue(metadata.get("document_name")),
                integerValue(metadata.get("chunk_index")),
                document.getScore(),
                document.getText()
        );
    }

    private List<SourceDto> keywordSearch(String query, int topK) {
        List<SourceDto> candidates = jdbcTemplate.query("""
                SELECT c.document_id, d.file_name, c.chunk_index, c.content
                FROM kb_chunks c
                JOIN kb_documents d ON d.id = c.document_id
                ORDER BY c.created_at DESC
                LIMIT 300
                """, (rs, rowNum) -> new SourceDto(
                rs.getString("document_id"),
                rs.getString("file_name"),
                rs.getInt("chunk_index"),
                null,
                rs.getString("content")
        ));

        return candidates.stream()
                .map(source -> new ScoredSource(source, keywordScore(query, source.content())))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredSource::score).reversed())
                .limit(topK)
                .map(scored -> new SourceDto(
                        scored.source().documentId(),
                        scored.source().documentName(),
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

    private void validateExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".txt") && !lower.endsWith(".md")) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "only .txt and .md are supported");
        }
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

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record ScoredSource(SourceDto source, double score) {
    }
}
