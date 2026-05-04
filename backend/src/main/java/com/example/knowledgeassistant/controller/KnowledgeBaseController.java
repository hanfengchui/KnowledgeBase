package com.example.knowledgeassistant.controller;

import com.example.knowledgeassistant.dto.CreateKnowledgeBaseRequest;
import com.example.knowledgeassistant.dto.KnowledgeBaseSummary;
import com.example.knowledgeassistant.dto.UpdateKnowledgeBaseRequest;
import com.example.knowledgeassistant.service.KnowledgeBaseService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @GetMapping
    public List<KnowledgeBaseSummary> list() {
        return knowledgeBaseService.listKnowledgeBases();
    }

    @PostMapping
    public KnowledgeBaseSummary create(@Valid @RequestBody CreateKnowledgeBaseRequest request) {
        return knowledgeBaseService.createKnowledgeBase(request);
    }

    @PatchMapping("/{id}")
    public KnowledgeBaseSummary update(
            @PathVariable("id") UUID knowledgeBaseId,
            @Valid @RequestBody UpdateKnowledgeBaseRequest request
    ) {
        return knowledgeBaseService.updateKnowledgeBase(knowledgeBaseId, request);
    }
}
