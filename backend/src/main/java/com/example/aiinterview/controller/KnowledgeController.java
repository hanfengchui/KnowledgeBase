package com.example.aiinterview.controller;

import com.example.aiinterview.dto.DocumentSummary;
import com.example.aiinterview.dto.ReindexResponse;
import com.example.aiinterview.dto.UploadResponse;
import com.example.aiinterview.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class KnowledgeController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping
    public UploadResponse upload(@RequestParam("file") MultipartFile file) {
        return knowledgeBaseService.ingest(file);
    }

    @GetMapping
    public List<DocumentSummary> list() {
        return knowledgeBaseService.listDocuments();
    }

    @PostMapping("/reindex")
    public ReindexResponse reindex() {
        return knowledgeBaseService.rebuildVectorIndex();
    }
}
