package com.example.knowledgeassistant.dto;

import java.util.List;
import java.util.UUID;

public record AssignUserRolesRequest(
        UUID tenantId,
        List<String> platformRoleCodes,
        List<String> tenantRoleCodes
) {
}
