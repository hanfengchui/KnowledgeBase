package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.security.AuthorizationCatalog;
import com.example.knowledgeassistant.security.CurrentUser;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DatabasePermissionService implements PermissionService {

    private static final Map<String, Set<String>> ROLE_PERMISSION_INDEX = AuthorizationCatalog.ROLES.stream()
            .collect(Collectors.toMap(
                    AuthorizationCatalog.RoleDefinition::code,
                    AuthorizationCatalog.RoleDefinition::permissionCodes
            ));

    private final JdbcTemplate jdbcTemplate;

    public DatabasePermissionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> getTenantPermissionCodes(CurrentUser currentUser) {
        if (currentUser.isPlatformAdmin()) {
            return AuthorizationCatalog.PERMISSIONS.stream()
                    .map(AuthorizationCatalog.PermissionDefinition::code)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }

        if (currentUser.tenantId() == null) {
            return Set.of();
        }

        return new LinkedHashSet<>(jdbcTemplate.query("""
                SELECT DISTINCT p.code
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                JOIN role_permissions rp ON rp.role_id = r.id
                JOIN permissions p ON p.id = rp.permission_id
                WHERE ur.user_id = ?
                  AND (ur.tenant_id = ? OR ur.tenant_id IS NULL)
                ORDER BY p.code
                """, (rs, rowNum) -> rs.getString("code"), currentUser.userId(), currentUser.tenantId()));
    }

    @Override
    public Set<String> getKnowledgeBasePermissionCodes(CurrentUser currentUser, UUID knowledgeBaseId) {
        verifyKnowledgeBaseScope(currentUser, knowledgeBaseId);

        LinkedHashSet<String> permissionCodes = new LinkedHashSet<>(getTenantPermissionCodes(currentUser));
        for (String roleCode : getKnowledgeBaseRoleCodes(currentUser, knowledgeBaseId)) {
            permissionCodes.addAll(ROLE_PERMISSION_INDEX.getOrDefault(roleCode, Set.of()));
        }
        return permissionCodes;
    }

    @Override
    public List<String> getKnowledgeBaseRoleCodes(CurrentUser currentUser, UUID knowledgeBaseId) {
        verifyKnowledgeBaseScope(currentUser, knowledgeBaseId);
        return jdbcTemplate.query("""
                SELECT role_code
                FROM knowledge_base_members
                WHERE knowledge_base_id = ?
                  AND user_id = ?
                ORDER BY role_code
                """, (rs, rowNum) -> rs.getString("role_code"), knowledgeBaseId, currentUser.userId());
    }

    @Override
    public boolean canAccessKnowledgeBase(CurrentUser currentUser, UUID knowledgeBaseId) {
        verifyKnowledgeBaseScope(currentUser, knowledgeBaseId);
        if (hasTenantPermission(currentUser, AuthorizationCatalog.PERMISSION_KB_READ)) {
            return true;
        }
        return !getKnowledgeBaseRoleCodes(currentUser, knowledgeBaseId).isEmpty();
    }

    @Override
    public boolean hasTenantPermission(CurrentUser currentUser, String permissionCode) {
        return getTenantPermissionCodes(currentUser).contains(permissionCode);
    }

    @Override
    public boolean hasKnowledgeBasePermission(CurrentUser currentUser, UUID knowledgeBaseId, String permissionCode) {
        return getKnowledgeBasePermissionCodes(currentUser, knowledgeBaseId).contains(permissionCode);
    }

    @Override
    public void requireTenantPermission(CurrentUser currentUser, String permissionCode) {
        if (!hasTenantPermission(currentUser, permissionCode)) {
            throw new ResponseStatusException(FORBIDDEN, "当前账号缺少权限: " + permissionCode);
        }
    }

    @Override
    public void requireKnowledgeBasePermission(CurrentUser currentUser, UUID knowledgeBaseId, String permissionCode) {
        if (!hasKnowledgeBasePermission(currentUser, knowledgeBaseId, permissionCode)) {
            throw new ResponseStatusException(FORBIDDEN, "当前账号缺少知识库权限: " + permissionCode);
        }
    }

    private void verifyKnowledgeBaseScope(CurrentUser currentUser, UUID knowledgeBaseId) {
        if (knowledgeBaseId == null) {
            throw new ResponseStatusException(NOT_FOUND, "未找到指定知识库");
        }

        if (currentUser.isPlatformAdmin()) {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM knowledge_bases WHERE id = ?",
                    Integer.class,
                    knowledgeBaseId
            );
            if (exists == null || exists == 0) {
                throw new ResponseStatusException(NOT_FOUND, "未找到指定知识库");
            }
            return;
        }

        if (currentUser.tenantId() == null) {
            throw new ResponseStatusException(FORBIDDEN, "当前账号未绑定租户上下文");
        }

        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM knowledge_bases WHERE id = ? AND tenant_id = ?",
                Integer.class,
                knowledgeBaseId,
                currentUser.tenantId()
        );
        if (exists == null || exists == 0) {
            throw new ResponseStatusException(NOT_FOUND, "未找到指定知识库");
        }
    }
}
