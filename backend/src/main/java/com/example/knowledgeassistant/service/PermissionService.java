package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.security.CurrentUser;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface PermissionService {

    Set<String> getTenantPermissionCodes(CurrentUser currentUser);

    Set<String> getKnowledgeBasePermissionCodes(CurrentUser currentUser, UUID knowledgeBaseId);

    List<String> getKnowledgeBaseRoleCodes(CurrentUser currentUser, UUID knowledgeBaseId);

    boolean canAccessKnowledgeBase(CurrentUser currentUser, UUID knowledgeBaseId);

    boolean hasTenantPermission(CurrentUser currentUser, String permissionCode);

    boolean hasKnowledgeBasePermission(CurrentUser currentUser, UUID knowledgeBaseId, String permissionCode);

    void requireTenantPermission(CurrentUser currentUser, String permissionCode);

    void requireKnowledgeBasePermission(CurrentUser currentUser, UUID knowledgeBaseId, String permissionCode);
}
