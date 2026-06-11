package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.AskRequest;
import com.example.knowledgeassistant.dto.AskResponse;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

    private final AgentService agentService;

    public ChatService(AgentService agentService) {
        this.agentService = agentService;
    }

    public AskResponse ask(AskRequest request) {
        return agentService.ask(request);
    }
}
