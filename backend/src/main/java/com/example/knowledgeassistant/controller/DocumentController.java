package com.example.knowledgeassistant.controller;

import com.example.knowledgeassistant.dto.DocumentSummary;
import com.example.knowledgeassistant.dto.ReindexResponse;
import com.example.knowledgeassistant.dto.UploadResponse;
import com.example.knowledgeassistant.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final KnowledgeBaseService knowledgeBaseService;

    public DocumentController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public UploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "knowledgeBaseId", required = false) UUID knowledgeBaseId
    ) {
        return knowledgeBaseService.ingest(file, knowledgeBaseId);
    }

    @GetMapping
    public List<DocumentSummary> list(@RequestParam(value = "knowledgeBaseId", required = false) UUID knowledgeBaseId) {
        return knowledgeBaseService.listDocuments(knowledgeBaseId);
    }

    @PostMapping("/reindex")
    public ReindexResponse reindex(@RequestParam(value = "knowledgeBaseId", required = false) UUID knowledgeBaseId) {
        return knowledgeBaseService.rebuildVectorIndex(knowledgeBaseId);
    }
}
