package com.example.knowledgeassistant.dto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record AuthMeResponse(
        UUID userId,
        String username,
        String displayName,
        String email,
        String status,
        boolean platformAdmin,
        UUID tenantId,
        String tenantCode,
        String tenantName,
        Set<String> roleCodes,
        Set<String> permissionCodes,
        List<AvailableTenantDto> availableTenants,
        List<KnowledgeBaseAccessDto> knowledgeBaseAccesses
) {
}
