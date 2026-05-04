package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentChunkerTest {

    @Test
    void chunksParagraphsWithoutLosingContent() {
        KnowledgeAssistantProperties properties = new KnowledgeAssistantProperties();
        properties.setMaxChunkChars(25);
        properties.setChunkOverlapChars(5);
        DocumentChunker chunker = new DocumentChunker(properties);

        List<TextChunk> chunks = chunker.chunk("""
                第一段介绍企业知识库。

                第二段介绍向量检索和 RAG。

                第三段介绍工具调用。
                """);

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(chunks).extracting(TextChunk::index).containsExactly(0, 1, 2);
        assertThat(chunks.get(0).content()).contains("第一段");
        assertThat(chunks.get(1).content()).contains("第二段");
    }

    @Test
    void splitsLargeParagraphWithOverlap() {
        KnowledgeAssistantProperties properties = new KnowledgeAssistantProperties();
        properties.setMaxChunkChars(20);
        properties.setChunkOverlapChars(5);
        DocumentChunker chunker = new DocumentChunker(properties);

        List<TextChunk> chunks = chunker.chunk("abcdefghijklmnopqrstuvwxyz");

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).content()).isEqualTo("abcdefghijklmnopqrst");
        assertThat(chunks.get(1).content()).isEqualTo("pqrstuvwxyz");
    }
}
