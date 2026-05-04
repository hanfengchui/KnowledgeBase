package com.example.knowledgeassistant.security;

import java.util.Set;
import java.util.UUID;

public record CurrentUser(
        UUID userId,
        String username,
        String displayName,
        UUID tenantId,
        String tenantCode,
        Set<String> roleCodes,
        String tokenId
) {

    public boolean hasRole(String roleCode) {
        return roleCodes != null && roleCodes.contains(roleCode);
    }

    public boolean isPlatformAdmin() {
        return hasRole("platform_admin");
    }
}
