package com.example.knowledgeassistant.service;

public interface DocumentParser {

    boolean supports(String fileName);

    String documentType();

    String extractText(String fileName, byte[] bytes);
}
