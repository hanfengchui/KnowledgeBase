package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.config.KnowledgeAssistantProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentChunker {

    private final KnowledgeAssistantProperties properties;

    public DocumentChunker(KnowledgeAssistantProperties properties) {
        this.properties = properties;
    }

    public List<TextChunk> chunk(String text) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("document text is blank");
        }

        int maxChars = Math.max(20, properties.getMaxChunkChars());
        int overlap = Math.max(0, Math.min(properties.getChunkOverlapChars(), maxChars / 3));
        String normalized = normalize(text);
        List<String> rawChunks = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\\n\\s*\\n")) {
            String cleaned = paragraph.trim();
            if (!StringUtils.hasText(cleaned)) {
                continue;
            }

            if (cleaned.length() > maxChars) {
                flush(current, rawChunks);
                splitLargeParagraph(cleaned, maxChars, overlap, rawChunks);
                continue;
            }

            int delimiterLength = current.isEmpty() ? 0 : 2;
            if (current.length() + delimiterLength + cleaned.length() > maxChars) {
                flush(current, rawChunks);
            }
            if (!current.isEmpty()) {
                current.append("\n\n");
            }
            current.append(cleaned);
        }
        flush(current, rawChunks);

        List<TextChunk> chunks = new ArrayList<>();
        for (int i = 0; i < rawChunks.size(); i++) {
            chunks.add(new TextChunk(i, rawChunks.get(i)));
        }
        return chunks;
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\uFEFF", "")
                .trim();
    }

    private void splitLargeParagraph(String paragraph, int maxChars, int overlap, List<String> chunks) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(paragraph.length(), start + maxChars);
            chunks.add(paragraph.substring(start, end).trim());
            if (end == paragraph.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
    }

    private void flush(StringBuilder builder, List<String> chunks) {
        if (!builder.isEmpty()) {
            chunks.add(builder.toString());
            builder.setLength(0);
        }
    }
}
